package org.triple.backend.file.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.triple.backend.file.config.S3BucketProperties;
import org.triple.backend.file.config.S3PrefixProperties;
import org.triple.backend.file.config.S3PresignProperties;
import org.triple.backend.file.config.S3UploadPolicyProperties;
import org.triple.backend.file.infra.exception.CopyFailedException;
import org.triple.backend.file.infra.exception.DeleteFailedException;
import org.triple.backend.file.infra.exception.InvalidKeyException;
import org.triple.backend.file.infra.exception.UploadFailedException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
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
    @DisplayName("받은 mimeType이 null일 시 예외를 반환한다.")
    void validateContentType_null_예외() {
        // given
        String mimeType = null;

        // when & then
        assertThatThrownBy(() -> s3BucketImpl.validateContentType(mimeType))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("받은 mimeType이 공백일 시 예외를 반환한다.")
    void validateContentType_blank_예외() {
        // given
        String mimeType = " ";

        // when & then
        assertThatThrownBy(() -> s3BucketImpl.validateContentType(mimeType))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("받은 mimeType이 허용값이 아닐 시 예외를 반환한다.")
    void validateContentType_미허용값_예외() {
        // given
        given(s3BucketProperties.getUploadPolicy()).willReturn(s3UploadPolicyProperties);
        given(s3UploadPolicyProperties.getAllowedContentTypes()).willReturn(List.of("image/jpeg", "image/png"));

        String mimeType = "image/gif";

        // when & then
        assertThatThrownBy(() -> s3BucketImpl.validateContentType(mimeType))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("받은 mimeType이 허용값이면 예외를 반환하지 않는다.")
    void validateContentType_허용값_정상() {
        // given
        given(s3BucketProperties.getUploadPolicy()).willReturn(s3UploadPolicyProperties);
        given(s3UploadPolicyProperties.getAllowedContentTypes()).willReturn(List.of("image/jpeg", "image/png"));

        String mimeType = "image/jpeg";

        // when & then
        assertThatCode(() -> s3BucketImpl.validateContentType(mimeType))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("pendingKey, mimeType 파라미터 사용 시 해당 파라미터 기반의 PresignedUrl이 반환된다.")
    void issuePresignedUrl_정상_반환() throws Exception {
        // given
        String pendingKey = "uploads/pending/1/test.jpg";
        String mimeType = "image/jpeg";
        String bucket = "triple-dev-s3";

        given(s3BucketProperties.getBucket()).willReturn(bucket);
        given(s3BucketProperties.getPresign()).willReturn(s3PresignProperties);
        given(s3PresignProperties.getPutExpireSeconds()).willReturn(180);

        PresignedPutObjectRequest presignedPutObjectRequest = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        given(presignedPutObjectRequest.url()).willReturn(new URL("https://example.com/upload"));
        given(presignedPutObjectRequest.expiration()).willReturn(Instant.parse("2030-01-01T00:00:00Z"));

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        given(s3Presigner.presignPutObject(captor.capture())).willReturn(presignedPutObjectRequest);

        // when
        PresignedUrl result = s3BucketImpl.issuePresignedUrl(pendingKey, mimeType);

        // then
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
    @DisplayName("사용자 소유 prefix가 아닐 경우 pendingKey 검증에서 예외를 반환한다.")
    void validatePendingKey_소유자불일치_예외() {
        // given
        given(s3BucketProperties.getPrefix()).willReturn(s3PrefixProperties);
        given(s3PrefixProperties.getPending()).willReturn("uploads/pending/");

        String pendingKey = "uploads/pending/2/test.jpg";
        Long userId = 1L;

        // when & then
        assertThatThrownBy(() -> s3BucketImpl.validatePendingKey(pendingKey, userId))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("S3에 파일이 없으면 업로드 검증에서 UploadFailedException(NOT_FOUND)을 반환한다.")
    void validateUploadedObject_파일없음_예외() {
        // given
        String pendingKey = "uploads/pending/1/test.jpg";
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().statusCode(404).message("no such key").build());

        // when & then
        assertThatThrownBy(() -> s3BucketImpl.validateUploadedObject(pendingKey))
                .isInstanceOf(UploadFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("copyObject에서 재시도 대상이 아닌 4xx 오류가 발생하면 CopyFailedException을 반환한다.")
    void copyObject_4xx_예외변환() {
        // given
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.copyObject(any(CopyObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(403).message("forbidden").build());

        // when & then
        assertThatThrownBy(() -> s3BucketImpl.copyObject("uploads/pending/1/a.jpg", "uploads/uploaded/1/a.jpg"))
                .isInstanceOf(CopyFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("deleteObject에서 재시도 대상이 아닌 4xx 오류가 발생하면 DeleteFailedException을 반환한다.")
    void deleteObject_4xx_예외변환() {
        // given
        given(s3BucketProperties.getBucket()).willReturn("triple-dev-s3");
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(400).message("bad request").build());

        // when & then
        assertThatThrownBy(() -> s3BucketImpl.deleteObject("uploads/uploaded/1/a.jpg"))
                .isInstanceOf(DeleteFailedException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}