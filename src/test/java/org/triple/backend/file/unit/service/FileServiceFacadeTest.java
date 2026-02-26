package org.triple.backend.file.unit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.triple.backend.file.dto.request.PresignedUrlRequestDto;
import org.triple.backend.file.dto.request.PresignedUrlRequestDtos;
import org.triple.backend.file.dto.request.UploadedKeysRequestDto;
import org.triple.backend.file.dto.response.FileUploadCompleteResponsesDto;
import org.triple.backend.file.dto.response.PresignedUrlFailedDto;
import org.triple.backend.file.dto.response.PresignedUrlResponse;
import org.triple.backend.file.dto.response.PresignedUrlResponsesDto;
import org.triple.backend.file.dto.response.PresignedUrlSuccessDto;
import org.triple.backend.file.dto.response.UploadFailed;
import org.triple.backend.file.dto.response.UploadResult;
import org.triple.backend.file.dto.response.UploadSuccess;
import org.triple.backend.file.infra.exception.FinalizeUploadException;
import org.triple.backend.file.service.FileService;
import org.triple.backend.file.service.FileServiceFacade;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileServiceFacadeTest {

    @Mock
    private FileService fileService;

    @InjectMocks
    private FileServiceFacade fileServiceFacade;

    @Test
    @DisplayName("presign request returns response in same order")
    void issuePutPresignedUrls() {
        Long userId = 1L;

        PresignedUrlRequestDto firstRequest = new PresignedUrlRequestDto("a.jpg", "image/jpeg");
        PresignedUrlRequestDto secondRequest = new PresignedUrlRequestDto("b.png", "image/png");
        PresignedUrlRequestDtos requestDtos = new PresignedUrlRequestDtos(List.of(firstRequest, secondRequest));

        PresignedUrlResponse firstResponse = new PresignedUrlSuccessDto(
                "a.jpg",
                "image/jpeg",
                "uploads/pending/1/a.jpg",
                "https://example.com/a",
                Instant.parse("2030-01-01T00:00:00Z")
        );
        PresignedUrlResponse secondResponse = new PresignedUrlFailedDto(
                "b.png",
                "image/png",
                400,
                "invalid mimeType"
        );

        given(fileService.issuePutPresignedUrl(firstRequest, userId)).willReturn(firstResponse);
        given(fileService.issuePutPresignedUrl(secondRequest, userId)).willReturn(secondResponse);

        PresignedUrlResponsesDto result = fileServiceFacade.issuePutPresignedUrls(requestDtos, userId);

        assertThat(result.presignedUrlResponses()).containsExactly(firstResponse, secondResponse);
    }

    @Test
    @DisplayName("complete upload returns both success and failure")
    void completeUploads() {
        Long userId = 1L;
        String firstPendingKey = "uploads/pending/1/a.jpg";
        String secondPendingKey = "uploads/pending/1/b.jpg";
        String firstUploadedKey = "uploads/uploaded/1/a.jpg";
        String firstUploadedUrl = "https://triple-dev-s3.s3.ap-northeast-2.amazonaws.com/uploads/uploaded/1/a.jpg";

        UploadedKeysRequestDto requestDto = new UploadedKeysRequestDto(List.of(firstPendingKey, secondPendingKey));

        doNothing().when(fileService).validateFinalizeUpload(firstPendingKey, userId);
        doThrow(new FinalizeUploadException(HttpStatus.NOT_FOUND, "uploaded file not found."))
                .when(fileService).validateFinalizeUpload(secondPendingKey, userId);

        given(fileService.finalizeUpload(firstPendingKey)).willReturn(firstUploadedKey);
        given(fileService.concatPrefix(firstUploadedKey)).willReturn(firstUploadedUrl);
        doNothing().when(fileService).saveFile(firstUploadedKey, firstUploadedUrl, userId);

        FileUploadCompleteResponsesDto result = fileServiceFacade.completeUploads(requestDto, userId);

        List<UploadResult> uploadResults = result.uploadResults();
        assertThat(uploadResults).containsExactly(
                new UploadSuccess(firstPendingKey, firstUploadedKey, firstUploadedUrl),
                new UploadFailed(secondPendingKey, HttpStatus.NOT_FOUND, "uploaded file not found.")
        );

        verify(fileService, never()).finalizeUpload(eq(secondPendingKey));
    }
}

