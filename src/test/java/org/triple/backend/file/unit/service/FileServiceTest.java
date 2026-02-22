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
import org.triple.backend.file.infra.exception.FinalizeUploadException;
import org.triple.backend.file.infra.exception.InvalidKeyException;
import org.triple.backend.file.repository.FileJpaRepository;
import org.triple.backend.file.service.FileService;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URL;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

        PresignedPutObjectRequest presignedPutObjectRequest = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        given(presignedPutObjectRequest.url()).willReturn(new URL("https://example.com/upload"));
        given(presignedPutObjectRequest.expiration()).willReturn(Instant.parse("2030-01-01T00:00:00Z"));

        PresignedUrl presignedUrl = new PresignedUrl(pendingKey, presignedPutObjectRequest);

        given(bucketKeyPublisher.publishPendingKey(requestDto.fileName(), userId)).willReturn(pendingKey);
        given(s3Bucket.issuePresignedUrl(pendingKey, requestDto.mimeType())).willReturn(presignedUrl);

        // when
        PresignedUrlResponseDto response = fileService.issuePutPresignedUrl(requestDto, userId);

        // then
        assertThat(response.success()).isTrue();
        assertThat(response.errorCode()).isNull();
        assertThat(response.key()).isEqualTo(pendingKey);
        assertThat(response.presignedUrl()).isEqualTo("https://example.com/upload");
    }

    @Test
    @DisplayName("Presigned URL 발급 실패 시 실패 응답을 반환한다.")
    void Presigned_URL_발급_실패_시_실패_응답을_반환한다() {
        // given
        Long userId = 1L;
        PresignedUrlRequestDto requestDto = new PresignedUrlRequestDto("test.gif", "image/gif");

        doThrow(new InvalidKeyException("허용되지 않는 mimeType입니다."))
                .when(s3Bucket).validateContentType(requestDto.mimeType());

        // when
        PresignedUrlResponseDto response = fileService.issuePutPresignedUrl(requestDto, userId);

        // then
        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.message()).isEqualTo("허용되지 않는 mimeType입니다.");
        verify(bucketKeyPublisher, never()).publishPendingKey(any(), any());
    }

    @Test
    @DisplayName("업로드 완료 검증 중 예상치 못한 예외는 FinalizeUploadException으로 변환한다.")
    void 업로드_완료_검증_중_예상치_못한_예외는_FinalizeUploadException으로_변환한다() {
        // given
        String pendingKey = "uploads/pending/1/test.jpg";
        Long userId = 1L;

        doThrow(new RuntimeException("s3 장애"))
                .when(s3Bucket).validatePendingKey(pendingKey, userId);

        // when & then
        assertThatThrownBy(() -> fileService.validateFinalizeUpload(pendingKey, userId))
                .isInstanceOf(FinalizeUploadException.class)
                .satisfies(exception -> {
                    FinalizeUploadException e = (FinalizeUploadException) exception;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(e.getCause()).hasMessage("s3 장애");
                });
    }

    @Test
    @DisplayName("업로드 완료 처리 성공 시 uploadedKey를 반환하고 복사/삭제를 수행한다.")
    void 업로드_완료_처리_성공_시_uploadedKey를_반환하고_복사_삭제를_수행한다() {
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
    @DisplayName("업로드 완료 키 생성 실패 시 InvalidKeyException을 던진다.")
    void 업로드_완료_키_생성_실패_시_InvalidKeyException을_던진다() {
        // given
        String pendingKey = "invalid-key";
        given(bucketKeyPublisher.publishUploadedKey(pendingKey))
                .willThrow(new IllegalArgumentException("키 형식이 올바르지 않습니다."));

        // when & then
        assertThatThrownBy(() -> fileService.finalizeUpload(pendingKey))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("파일 저장 성공 시 DB에 파일 엔티티를 저장한다.")
    void 파일_저장_성공_시_DB에_파일_엔티티를_저장한다() {
        // given
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        Long userId = 1L;
        given(fileJpaRepository.save(any(File.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        fileService.saveFile(uploadedKey, userId);

        // then
        verify(fileJpaRepository, times(1))
                .save(any(File.class));
    }

    @Test
    @DisplayName("DB 저장 실패 시 S3 삭제로 보상하고 FinalizeUploadException을 던진다.")
    void DB_저장_실패_시_S3_삭제로_보상하고_FinalizeUploadException을_던진다() {
        // given
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        Long userId = 1L;

        given(fileJpaRepository.save(any(File.class)))
                .willThrow(new RuntimeException("DB 저장 실패"));

        // when & then
        assertThatThrownBy(() -> fileService.saveFile(uploadedKey, userId))
                .isInstanceOf(FinalizeUploadException.class)
                .satisfies(exception -> {
                    FinalizeUploadException e = (FinalizeUploadException) exception;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(e.getCause()).hasMessage("DB 저장 실패");
                });

        verify(s3Bucket, times(1)).deleteObject(uploadedKey);
    }

    @Test
    @DisplayName("파일 저장 시 ownerId가 잘못되면 InvalidKeyException을 던진다.")
    void 파일_저장_시_ownerId가_잘못되면_InvalidKeyException을_던진다() {
        // given
        String uploadedKey = "uploads/uploaded/1/test.jpg";

        // when & then
        assertThatThrownBy(() -> fileService.saveFile(uploadedKey, null))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(fileJpaRepository, never()).save(any(File.class));
    }
}
