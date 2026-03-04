package org.triple.backend.file.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.triple.backend.file.config.property.S3BucketProperties;
import org.triple.backend.file.config.property.S3PrefixProperties;
import org.triple.backend.file.config.property.S3PresignProperties;
import org.triple.backend.file.config.property.S3UploadPolicyProperties;
import org.triple.backend.file.infra.exception.CopyFailedException;
import org.triple.backend.file.infra.exception.DeleteFailedException;
import org.triple.backend.file.infra.exception.InvalidKeyException;
import org.triple.backend.file.infra.exception.UploadFailedException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class S3BucketImplTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3BucketProperties s3BucketProperties;

    @Mock
    private S3UploadPolicyProperties s3UploadPolicyProperties;

    @Mock
    private S3PresignProperties s3PresignProperties;

    @Mock
    private S3PrefixProperties s3PrefixProperties;

    @InjectMocks
    private S3BucketImpl s3BucketImpl;

    @Test
    @DisplayName("받은 mimeType이 null이면 예외를 반환한다.")
    void 받은_mimeType이_null이면_예외를_반환한다() {
        assertThatThrownBy(() -> s3BucketImpl.validateContentType(null))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("받은 mimeType이 공백이면 예외를 반환한다.")
    void 받은_mimeType이_공백이면_예외를_반환한다() {
        assertThatThrownBy(() -> s3BucketImpl.validateContentType(" "))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("받은 mimeType이 허용값이 아니면 예외를 반환한다.")
    void 받은_mimeType이_허용값이_아니면_예외를_반환한다() {
        given(s3BucketProperties.getUploadPolicy()).willReturn(s3UploadPolicyProperties);
        given(s3UploadPolicyProperties.allowedContentTypes()).willReturn(List.of("image/jpeg", "image/png"));

        assertThatThrownBy(() -> s3BucketImpl.validateContentType("image/gif"))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("받은 mimeType이 허용값이면 예외를 반환하지 않는다.")
    void 받은_mimeType이_허용값이면_예외를_반환하지_않는다() {
        given(s3BucketProperties.getUploadPolicy()).willReturn(s3UploadPolicyProperties);
        given(s3UploadPolicyProperties.allowedContentTypes()).willReturn(List.of("image/jpeg", "image/png"));

        assertThatCode(() -> s3BucketImpl.validateContentType("image/jpeg"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("pendingKey와 mimeType으로 PresignedUrl을 생성한다.")
    void pendingKey와_mimeType으로_PresignedUrl을_생성한다() throws Exception {
        String pendingKey = "uploads/pending/1/test.jpg";
        String mimeType = "image/jpeg";
        String bucket = "triple-dev-s3";

        given(s3BucketProperties.getBucket()).willReturn(bucket);
        given(s3BucketProperties.getPresign()).willReturn(s3PresignProperties);
        given(s3PresignProperties.putExpireSeconds()).willReturn(180);

        PresignedPutObjectRequest presignedPutObjectRequest = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        given(presignedPutObjectRequest.url()).willReturn(new URL("https://example.com/upload"));
        given(presignedPutObjectRequest.expiration()).willReturn(Instant.parse("2030-01-01T00:00:00Z"));

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        given(s3Presigner.presignPutObject(captor.capture())).willReturn(presignedPutObjectRequest);

        PresignedUrl result = s3BucketImpl.issuePresignedUrl(pendingKey, mimeType);

        assertThat(result.key()).isEqualTo(pendingKey);
        assertThat(result.presignedUrl()).isEqualTo("https://example.com/upload");
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));

        PutObjectPresignRequest actualPresignRequest = captor.getValue();
        assertThat(actualPresignRequest.signatureDuration()).isEqualTo(Duration.ofSeconds(180));

        PutObjectRequest putObjectRequest = actualPresignRequest.putObjectRequest();
        assertThat(putObjectRequest.bucket()).isEqualTo(bucket);
        assertThat(putObjectRequest.key()).isEqualTo(pendingKey);
        assertThat(putObjectRequest.contentType()).isEqualTo(mimeType);
    }

    @Test
    @DisplayName("pendingKey가 null이면 예외를 반환한다.")
    void pendingKey가_null이면_예외를_반환한다() {
        assertThatThrownBy(() -> s3BucketImpl.validatePendingKey(null, 1L))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("pendingKey prefix와 userId가 일치하지 않으면 예외를 반환한다.")
    void pendingKey_prefix와_userId가_일치하지_않으면_예외를_반환한다() {
        given(s3BucketProperties.getPrefix()).willReturn(s3PrefixProperties);
        given(s3PrefixProperties.pending()).willReturn("uploads/pending/");

        assertThatThrownBy(() -> s3BucketImpl.validatePendingKey("uploads/pending/2/test.jpg", 1L))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("pendingKey prefix와 userId가 일치하면 검증을 통과한다.")
    void pendingKey_prefix와_userId가_일치하면_검증을_통과한다() {
        given(s3BucketProperties.getPrefix()).willReturn(s3PrefixProperties);
        given(s3PrefixProperties.pending()).willReturn("uploads/pending/");

        assertThatCode(() -> s3BucketImpl.validatePendingKey("uploads/pending/1/test.jpg", 1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("업로드된 파일이 없으면 UploadFailedException(NOT_FOUND)을 반환한다.")
    void 업로드된_파일이_없으면_UploadFailedException_NOT_FOUND를_반환한다() {
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().statusCode(404).message("no such key").build());

        assertThatThrownBy(() -> s3BucketImpl.validateUploadedObject("uploads/pending/1/test.jpg"))
                .isInstanceOf(UploadFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("headObject가 성공하면 업로드 검증을 통과한다.")
    void headObject가_성공하면_업로드_검증을_통과한다() {
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().build());

        assertThatCode(() -> s3BucketImpl.validateUploadedObject("uploads/pending/1/test.jpg"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("headObject에서 5xx가 발생하면 재시도 대상 예외를 그대로 전파한다.")
    void headObject에서_5xx가_발생하면_재시도_대상_예외를_그대로_전파한다() {
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(500).message("internal").build());

        assertThatThrownBy(() -> s3BucketImpl.validateUploadedObject("uploads/pending/1/test.jpg"))
                .isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("headObject에서 비재시도 비표준 status가 발생하면 BAD_REQUEST로 변환한다.")
    void headObject에서_비재시도_비표준_status가_발생하면_BAD_REQUEST로_변환한다() {
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(499).message("unknown").build());

        assertThatThrownBy(() -> s3BucketImpl.validateUploadedObject("uploads/pending/1/test.jpg"))
                .isInstanceOf(UploadFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("copyObject에서 비재시도 4xx가 발생하면 CopyFailedException을 반환한다.")
    void copyObject에서_비재시도_4xx가_발생하면_CopyFailedException을_반환한다() {
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.copyObject(any(CopyObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(403).message("forbidden").build());

        assertThatThrownBy(() -> s3BucketImpl.copyObject("uploads/pending/1/a.jpg", "uploads/uploaded/1/a.jpg"))
                .isInstanceOf(CopyFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("copyObject에서 5xx가 발생하면 재시도 대상 예외를 그대로 전파한다.")
    void copyObject에서_5xx가_발생하면_재시도_대상_예외를_그대로_전파한다() {
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.copyObject(any(CopyObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(500).message("internal").build());

        assertThatThrownBy(() -> s3BucketImpl.copyObject("uploads/pending/1/a.jpg", "uploads/uploaded/1/a.jpg"))
                .isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("copyObject에서 비재시도 비표준 status가 발생하면 BAD_GATEWAY로 변환한다.")
    void copyObject에서_비재시도_비표준_status가_발생하면_BAD_GATEWAY로_변환한다() {
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.copyObject(any(CopyObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(499).message("unknown").build());

        assertThatThrownBy(() -> s3BucketImpl.copyObject("uploads/pending/1/a.jpg", "uploads/uploaded/1/a.jpg"))
                .isInstanceOf(CopyFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("deleteObject에서 비재시도 4xx가 발생하면 DeleteFailedException을 반환한다.")
    void deleteObject에서_비재시도_4xx가_발생하면_DeleteFailedException을_반환한다() {
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(400).message("bad request").build());

        assertThatThrownBy(() -> s3BucketImpl.deleteObject("uploads/uploaded/1/a.jpg"))
                .isInstanceOf(DeleteFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("deleteObject에서 5xx가 발생하면 재시도 대상 예외를 그대로 전파한다.")
    void deleteObject에서_5xx가_발생하면_재시도_대상_예외를_그대로_전파한다() {
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(500).message("internal").build());

        assertThatThrownBy(() -> s3BucketImpl.deleteObject("uploads/uploaded/1/a.jpg"))
                .isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("deleteObject에서 비재시도 비표준 status가 발생하면 BAD_GATEWAY로 변환한다.")
    void deleteObject에서_비재시도_비표준_status가_발생하면_BAD_GATEWAY로_변환한다() {
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(499).message("unknown").build());

        assertThatThrownBy(() -> s3BucketImpl.deleteObject("uploads/uploaded/1/a.jpg"))
                .isInstanceOf(DeleteFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("headObject 재시도 소진(S3Exception) 시 UploadFailedException으로 복구한다.")
    void headObject_재시도_소진_S3Exception_시_UploadFailedException으로_복구한다() {
        S3Exception s3Exception = (S3Exception) S3Exception.builder().statusCode(500).message("fail").build();

        assertThatThrownBy(() -> s3BucketImpl.recoverUploadFailed(s3Exception, "uploads/pending/1/a.jpg"))
                .isInstanceOf(UploadFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("headObject 재시도 소진(SdkClientException) 시 UploadFailedException으로 복구한다.")
    void headObject_재시도_소진_SdkClientException_시_UploadFailedException으로_복구한다() {
        SdkClientException sdkClientException = SdkClientException.builder().message("network").build();

        assertThatThrownBy(() -> s3BucketImpl.recoverUploadFailed(sdkClientException, "uploads/pending/1/a.jpg"))
                .isInstanceOf(UploadFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("copyObject 재시도 소진(S3Exception) 시 CopyFailedException으로 복구한다.")
    void copyObject_재시도_소진_S3Exception_시_CopyFailedException으로_복구한다() {
        S3Exception s3Exception = (S3Exception) S3Exception.builder().statusCode(500).message("fail").build();

        assertThatThrownBy(() -> s3BucketImpl.recoverCopyFailed(
                s3Exception, "uploads/pending/1/a.jpg", "uploads/uploaded/1/a.jpg"))
                .isInstanceOf(CopyFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("copyObject 재시도 소진(SdkClientException) 시 CopyFailedException으로 복구한다.")
    void copyObject_재시도_소진_SdkClientException_시_CopyFailedException으로_복구한다() {
        SdkClientException sdkClientException = SdkClientException.builder().message("network").build();

        assertThatThrownBy(() -> s3BucketImpl.recoverCopyFailed(
                sdkClientException, "uploads/pending/1/a.jpg", "uploads/uploaded/1/a.jpg"))
                .isInstanceOf(CopyFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("deleteObject 재시도 소진(S3Exception) 시 DeleteFailedException으로 복구한다.")
    void deleteObject_재시도_소진_S3Exception_시_DeleteFailedException으로_복구한다() {
        S3Exception s3Exception = (S3Exception) S3Exception.builder().statusCode(500).message("fail").build();

        assertThatThrownBy(() -> s3BucketImpl.recoverDeleteFailed(s3Exception, "uploads/uploaded/1/a.jpg"))
                .isInstanceOf(DeleteFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("deleteObject 재시도 소진(SdkClientException) 시 DeleteFailedException으로 복구한다.")
    void deleteObject_재시도_소진_SdkClientException_시_DeleteFailedException으로_복구한다() {
        SdkClientException sdkClientException = SdkClientException.builder().message("network").build();

        assertThatThrownBy(() -> s3BucketImpl.recoverDeleteFailed(sdkClientException, "uploads/uploaded/1/a.jpg"))
                .isInstanceOf(DeleteFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
