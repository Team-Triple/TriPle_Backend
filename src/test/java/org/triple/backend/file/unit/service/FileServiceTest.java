package org.triple.backend.file.unit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.triple.backend.file.dto.request.PresignedUrlRequestDto;
import org.triple.backend.file.dto.response.PresignedUrlResponseDto;
import org.triple.backend.file.entity.File;
import org.triple.backend.file.infra.BucketKeyPublisher;
import org.triple.backend.file.infra.PresignedUrl;
import org.triple.backend.file.infra.S3Bucket;
import org.triple.backend.file.infra.exception.CopyFailedException;
import org.triple.backend.file.infra.exception.FinalizeUploadException;
import org.triple.backend.file.infra.exception.InvalidKeyException;
import org.triple.backend.file.repository.FileJpaRepository;
import org.triple.backend.file.service.FileService;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URL;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private S3Bucket s3Bucket;

    @Mock
    private BucketKeyPublisher bucketKeyPublisher;

    @Mock
    private FileJpaRepository fileJpaRepository;

    @InjectMocks
    private FileService fileService;

    @Test
    @DisplayName("Presigned URL 발급 성공 시 성공 응답을 반환한다.")
    void Presigned_URL_발급_성공_시_성공_응답을_반환한다() throws Exception {
        // given
        Long userId = 1L;
        PresignedUrlRequestDto requestDto = new PresignedUrlRequestDto("test.jpg", "image/jpeg");
        String pendingKey = "uploads/pending/1/test.jpg";

        PresignedPutObjectRequest awsRequest = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        given(awsRequest.url()).willReturn(new URL("https://example.com/upload"));
        given(awsRequest.expiration()).willReturn(Instant.parse("2030-01-01T00:00:00Z"));
        PresignedUrl presignedUrl = new PresignedUrl(pendingKey, awsRequest);

        doNothing().when(s3Bucket).validateContentType("image/jpeg");
        given(bucketKeyPublisher.publishPendingKey(requestDto.fileName(), userId)).willReturn(pendingKey);
        given(s3Bucket.issuePresignedUrl(pendingKey, requestDto.mimeType())).willReturn(presignedUrl);

        // when
        PresignedUrlResponseDto response = fileService.issuePutPresignedUrl(requestDto, userId);

        // then
        assertThat(response.success()).isTrue();
        assertThat(response.key()).isEqualTo(pendingKey);
        assertThat(response.errorCode()).isNull();
        assertThat(response.message()).isNull();
    }

    @Test
    @DisplayName("Presigned URL 발급 중 FinalizeUploadException 발생 시 상태코드와 메시지를 반환한다.")
    void Presigned_URL_발급_중_FinalizeUploadException_발생_시_상태코드와_메시지를_반환한다() {
        // given
        Long userId = 1L;
        PresignedUrlRequestDto requestDto = new PresignedUrlRequestDto("test.jpg", "image/jpeg");
        doThrow(new FinalizeUploadException(HttpStatus.NOT_FOUND, "찾을 수 없습니다."))
                .when(s3Bucket).validateContentType("image/jpeg");

        // when
        PresignedUrlResponseDto response = fileService.issuePutPresignedUrl(requestDto, userId);

        // then
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo(404);
        assertThat(response.message()).isEqualTo("찾을 수 없습니다.");
    }

    @Test
    @DisplayName("Presigned URL 발급 중 IllegalArgumentException 발생 시 BAD_REQUEST를 반환한다.")
    void Presigned_URL_발급_중_IllegalArgumentException_발생_시_BAD_REQUEST를_반환한다() {
        // given
        Long userId = 1L;
        PresignedUrlRequestDto requestDto = new PresignedUrlRequestDto("test.jpg", "image/jpeg");
        given(bucketKeyPublisher.publishPendingKey(requestDto.fileName(), userId))
                .willThrow(new IllegalArgumentException("잘못된 파일명"));

        // when
        PresignedUrlResponseDto response = fileService.issuePutPresignedUrl(requestDto, userId);

        // then
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("잘못된 파일명");
    }

    @Test
    @DisplayName("Presigned URL 발급 중 메시지 없는 RuntimeException 발생 시 기본 메시지를 반환한다.")
    void Presigned_URL_발급_중_메시지_없는_RuntimeException_발생_시_기본_메시지를_반환한다() {
        // given
        Long userId = 1L;
        PresignedUrlRequestDto requestDto = new PresignedUrlRequestDto("test.jpg", "image/jpeg");
        doThrow(new RuntimeException())
                .when(s3Bucket).validateContentType("image/jpeg");

        // when
        PresignedUrlResponseDto response = fileService.issuePutPresignedUrl(requestDto, userId);

        // then
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo(500);
        assertThat(response.message()).isEqualTo("요청 처리에 실패했습니다.");
    }

    @Test
    @DisplayName("업로드 완료 검증 성공 시 예외 없이 통과한다.")
    void 업로드_완료_검증_성공_시_예외_없이_통과한다() {
        // given
        String pendingKey = "uploads/pending/1/test.jpg";
        Long userId = 1L;
        doNothing().when(s3Bucket).validatePendingKey(pendingKey, userId);
        doNothing().when(s3Bucket).validateUploadedObject(pendingKey);

        // when & then
        assertThatCode(() -> fileService.validateFinalizeUpload(pendingKey, userId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("업로드 완료 검증 중 FinalizeUploadException은 그대로 전파한다.")
    void 업로드_완료_검증_중_FinalizeUploadException은_그대로_전파한다() {
        // given
        String pendingKey = "uploads/pending/1/test.jpg";
        Long userId = 1L;
        FinalizeUploadException exception = new FinalizeUploadException(HttpStatus.BAD_REQUEST, "검증 실패");
        doThrow(exception).when(s3Bucket).validateUploadedObject(pendingKey);

        // when & then
        assertThatThrownBy(() -> fileService.validateFinalizeUpload(pendingKey, userId))
                .isSameAs(exception);
    }

    @Test
    @DisplayName("업로드 완료 검증 중 예상치 못한 예외는 INTERNAL_SERVER_ERROR로 래핑한다.")
    void 업로드_완료_검증_중_예상치_못한_예외는_INTERNAL_SERVER_ERROR로_래핑한다() {
        // given
        String pendingKey = "uploads/pending/1/test.jpg";
        Long userId = 1L;
        doThrow(new RuntimeException("s3 장애"))
                .when(s3Bucket).validatePendingKey(pendingKey, userId);

        // when & then
        assertThatThrownBy(() -> fileService.validateFinalizeUpload(pendingKey, userId))
                .isInstanceOf(FinalizeUploadException.class)
                .satisfies(throwable -> {
                    FinalizeUploadException exception = (FinalizeUploadException) throwable;
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getCause()).hasMessage("s3 장애");
                });
    }

    @Test
    @DisplayName("업로드 완료 처리 성공 시 복사 후 삭제하고 uploadedKey를 반환한다.")
    void 업로드_완료_처리_성공_시_복사_후_삭제하고_uploadedKey를_반환한다() {
        // given
        String pendingKey = "uploads/pending/1/test.jpg";
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        given(bucketKeyPublisher.publishUploadedKey(pendingKey)).willReturn(uploadedKey);

        // when
        String result = fileService.finalizeUpload(pendingKey);

        // then
        assertThat(result).isEqualTo(uploadedKey);
        verify(s3Bucket, times(1)).copyObject(pendingKey, uploadedKey);
        verify(s3Bucket, times(1)).deleteObject(pendingKey);
    }

    @Test
    @DisplayName("업로드 완료 처리 중 FinalizeUploadException은 그대로 전파한다.")
    void 업로드_완료_처리_중_FinalizeUploadException은_그대로_전파한다() {
        // given
        String pendingKey = "uploads/pending/1/test.jpg";
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        given(bucketKeyPublisher.publishUploadedKey(pendingKey)).willReturn(uploadedKey);
        CopyFailedException exception = new CopyFailedException(HttpStatus.BAD_GATEWAY, "복사 실패");
        doThrow(exception).when(s3Bucket).copyObject(pendingKey, uploadedKey);

        // when & then
        assertThatThrownBy(() -> fileService.finalizeUpload(pendingKey))
                .isSameAs(exception);
    }

    @Test
    @DisplayName("업로드 완료 처리 중 IllegalArgumentException은 InvalidKeyException으로 변환한다.")
    void 업로드_완료_처리_중_IllegalArgumentException은_InvalidKeyException으로_변환한다() {
        // given
        given(bucketKeyPublisher.publishUploadedKey("invalid"))
                .willThrow(new IllegalArgumentException("잘못된 키"));

        // when & then
        assertThatThrownBy(() -> fileService.finalizeUpload("invalid"))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("업로드 완료 처리 중 예상치 못한 예외는 INTERNAL_SERVER_ERROR로 래핑한다.")
    void 업로드_완료_처리_중_예상치_못한_예외는_INTERNAL_SERVER_ERROR로_래핑한다() {
        // given
        String pendingKey = "uploads/pending/1/test.jpg";
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        given(bucketKeyPublisher.publishUploadedKey(pendingKey)).willReturn(uploadedKey);
        doThrow(new RuntimeException("복사 중 장애"))
                .when(s3Bucket).copyObject(pendingKey, uploadedKey);

        // when & then
        assertThatThrownBy(() -> fileService.finalizeUpload(pendingKey))
                .isInstanceOf(FinalizeUploadException.class)
                .satisfies(throwable -> {
                    FinalizeUploadException exception = (FinalizeUploadException) throwable;
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getCause()).hasMessage("복사 중 장애");
                });
    }

    @Test
    @DisplayName("파일 저장 성공 시 DB에 저장한다.")
    void 파일_저장_성공_시_DB에_저장한다() {
        // given
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        Long userId = 1L;
        given(fileJpaRepository.save(any(File.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        fileService.saveFile(uploadedKey, userId);

        // then
        verify(fileJpaRepository, times(1)).save(any(File.class));
    }

    @Test
    @DisplayName("파일 저장 시 잘못된 인자는 InvalidKeyException으로 변환한다.")
    void 파일_저장_시_잘못된_인자는_InvalidKeyException으로_변환한다() {
        assertThatThrownBy(() -> fileService.saveFile("uploads/uploaded/1/test.jpg", null))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(fileJpaRepository, never()).save(any(File.class));
    }

    @Test
    @DisplayName("DB 저장 실패 시 업로드 파일 삭제로 보상 후 FinalizeUploadException을 던진다.")
    void DB_저장_실패_시_업로드_파일_삭제로_보상_후_FinalizeUploadException을_던진다() {
        // given
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        Long userId = 1L;
        given(fileJpaRepository.save(any(File.class)))
                .willThrow(new RuntimeException("DB 저장 실패"));
        doNothing().when(s3Bucket).deleteObject(uploadedKey);

        // when & then
        assertThatThrownBy(() -> fileService.saveFile(uploadedKey, userId))
                .isInstanceOf(FinalizeUploadException.class)
                .satisfies(throwable -> {
                    FinalizeUploadException exception = (FinalizeUploadException) throwable;
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getCause()).hasMessage("DB 저장 실패");
                });
    }

    @Test
    @DisplayName("DB 저장 실패 후 보상 삭제까지 실패하면 원인 예외에 suppressed로 추가한다.")
    void DB_저장_실패_후_보상_삭제까지_실패하면_원인_예외에_suppressed로_추가한다() {
        // given
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        Long userId = 1L;
        RuntimeException dbException = new RuntimeException("DB 저장 실패");
        RuntimeException deleteException = new RuntimeException("S3 삭제 실패");

        given(fileJpaRepository.save(any(File.class))).willThrow(dbException);
        doThrow(deleteException).when(s3Bucket).deleteObject(uploadedKey);

        // when & then
        assertThatThrownBy(() -> fileService.saveFile(uploadedKey, userId))
                .isInstanceOf(FinalizeUploadException.class)
                .satisfies(throwable -> {
                    FinalizeUploadException exception = (FinalizeUploadException) throwable;
                    assertThat(exception.getCause()).isSameAs(dbException);
                    assertThat(dbException.getSuppressed()).containsExactly(deleteException);
                });
    }
}
