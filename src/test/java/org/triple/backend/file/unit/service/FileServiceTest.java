package org.triple.backend.file.unit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.triple.backend.file.dto.request.PresignedUrlRequestDto;
import org.triple.backend.file.dto.response.PresignedUrlFailedDto;
import org.triple.backend.file.dto.response.PresignedUrlResponse;
import org.triple.backend.file.dto.response.PresignedUrlSuccessDto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    @DisplayName("issue presigned url success")
    void issuePutPresignedUrlSuccess() throws Exception {
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

        PresignedUrlResponse response = fileService.issuePutPresignedUrl(requestDto, userId);

        assertThat(response).isInstanceOfSatisfying(PresignedUrlSuccessDto.class, success -> {
            assertThat(success.fileName()).isEqualTo("test.jpg");
            assertThat(success.key()).isEqualTo(pendingKey);
            assertThat(success.presignedUrl()).isEqualTo("https://example.com/upload");
        });
    }

    @Test
    @DisplayName("issue presigned url returns failed dto on exception")
    void issuePutPresignedUrlFailure() {
        Long userId = 1L;
        PresignedUrlRequestDto requestDto = new PresignedUrlRequestDto("test.jpg", "image/jpeg");
        doThrow(new FinalizeUploadException(HttpStatus.NOT_FOUND, "not found"))
                .when(s3Bucket).validateContentType("image/jpeg");

        PresignedUrlResponse response = fileService.issuePutPresignedUrl(requestDto, userId);

        assertThat(response).isInstanceOfSatisfying(PresignedUrlFailedDto.class, failed -> {
            assertThat(failed.errorCode()).isEqualTo(404);
            assertThat(failed.message()).isEqualTo("not found");
        });
    }

    @Test
    @DisplayName("validate finalize upload success")
    void validateFinalizeUploadSuccess() {
        String pendingKey = "uploads/pending/1/test.jpg";
        Long userId = 1L;
        doNothing().when(s3Bucket).validatePendingKey(pendingKey, userId);
        doNothing().when(s3Bucket).validateUploadedObject(pendingKey);

        assertThatCode(() -> fileService.validateFinalizeUpload(pendingKey, userId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate finalize upload propagates finalize exception")
    void validateFinalizeUploadPropagates() {
        String pendingKey = "uploads/pending/1/test.jpg";
        Long userId = 1L;
        FinalizeUploadException exception = new FinalizeUploadException(HttpStatus.BAD_REQUEST, "validate fail");
        doThrow(exception).when(s3Bucket).validateUploadedObject(pendingKey);

        assertThatThrownBy(() -> fileService.validateFinalizeUpload(pendingKey, userId)).isSameAs(exception);
    }

    @Test
    @DisplayName("finalize upload success")
    void finalizeUploadSuccess() {
        String pendingKey = "uploads/pending/1/test.jpg";
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        given(bucketKeyPublisher.publishUploadedKey(pendingKey)).willReturn(uploadedKey);

        String result = fileService.finalizeUpload(pendingKey);

        assertThat(result).isEqualTo(uploadedKey);
        verify(s3Bucket, times(1)).copyObject(pendingKey, uploadedKey);
        verify(s3Bucket, times(1)).deleteObject(pendingKey);
    }

    @Test
    @DisplayName("finalize upload propagates finalize exception")
    void finalizeUploadPropagates() {
        String pendingKey = "uploads/pending/1/test.jpg";
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        given(bucketKeyPublisher.publishUploadedKey(pendingKey)).willReturn(uploadedKey);
        CopyFailedException exception = new CopyFailedException(HttpStatus.BAD_GATEWAY, "copy fail");
        doThrow(exception).when(s3Bucket).copyObject(pendingKey, uploadedKey);

        assertThatThrownBy(() -> fileService.finalizeUpload(pendingKey)).isSameAs(exception);
    }

    @Test
    @DisplayName("finalize upload wraps illegal argument")
    void finalizeUploadInvalidKey() {
        given(bucketKeyPublisher.publishUploadedKey("invalid"))
                .willThrow(new IllegalArgumentException("bad key"));

        assertThatThrownBy(() -> fileService.finalizeUpload("invalid"))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("save file success")
    void saveFileSuccess() {
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        String uploadedUrl = "https://triple-dev-s3.s3.ap-northeast-2.amazonaws.com/uploads/uploaded/1/test.jpg";
        Long userId = 1L;
        given(fileJpaRepository.save(any(File.class))).willAnswer(invocation -> invocation.getArgument(0));

        fileService.saveFile(uploadedKey, uploadedUrl, userId);

        verify(fileJpaRepository, times(1)).save(any(File.class));
    }

    @Test
    @DisplayName("save file invalid argument")
    void saveFileInvalidArgument() {
        assertThatThrownBy(() -> fileService.saveFile("uploads/uploaded/1/test.jpg", "https://example.com/a.jpg", null))
                .isInstanceOf(InvalidKeyException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(fileJpaRepository, never()).save(any(File.class));
    }

    @Test
    @DisplayName("save file db failure deletes uploaded object by key")
    void saveFileDbFailure() {
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        String uploadedUrl = "https://triple-dev-s3.s3.ap-northeast-2.amazonaws.com/uploads/uploaded/1/test.jpg";
        Long userId = 1L;

        given(fileJpaRepository.save(any(File.class))).willThrow(new RuntimeException("db fail"));
        doNothing().when(s3Bucket).deleteObject(uploadedKey);

        assertThatThrownBy(() -> fileService.saveFile(uploadedKey, uploadedUrl, userId))
                .isInstanceOf(FinalizeUploadException.class)
                .satisfies(throwable -> {
                    FinalizeUploadException exception = (FinalizeUploadException) throwable;
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getCause()).hasMessage("db fail");
                });
    }

    @Test
    @DisplayName("save file db failure keeps delete failure as suppressed")
    void saveFileDbFailureWithCompensationFailure() {
        String uploadedKey = "uploads/uploaded/1/test.jpg";
        String uploadedUrl = "https://triple-dev-s3.s3.ap-northeast-2.amazonaws.com/uploads/uploaded/1/test.jpg";
        Long userId = 1L;
        RuntimeException dbException = new RuntimeException("db fail");
        RuntimeException deleteException = new RuntimeException("delete fail");

        given(fileJpaRepository.save(any(File.class))).willThrow(dbException);
        doThrow(deleteException).when(s3Bucket).deleteObject(uploadedKey);

        assertThatThrownBy(() -> fileService.saveFile(uploadedKey, uploadedUrl, userId))
                .isInstanceOf(FinalizeUploadException.class)
                .satisfies(throwable -> {
                    FinalizeUploadException exception = (FinalizeUploadException) throwable;
                    assertThat(exception.getCause()).isSameAs(dbException);
                    assertThat(dbException.getSuppressed()).containsExactly(deleteException);
                });
    }
}
