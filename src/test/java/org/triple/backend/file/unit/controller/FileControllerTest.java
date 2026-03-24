package org.triple.backend.file.unit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.file.controller.FileController;
import org.triple.backend.file.dto.request.PresignedUrlRequestDtos;
import org.triple.backend.file.dto.request.UploadedKeysRequestDto;
import org.triple.backend.file.dto.response.FileUploadCompleteResponsesDto;
import org.triple.backend.file.dto.response.PresignedUrlResponsesDto;
import org.triple.backend.file.dto.response.PresignedUrlSuccessDto;
import org.triple.backend.file.dto.response.UploadSuccess;
import org.triple.backend.file.service.FileServiceFacade;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
class FileControllerTest extends ControllerTest {

    @MockitoBean
    private FileServiceFacade fileServiceFacade;

    @Test
    @DisplayName("upload presign returns list")
    void issuePutPresignedUrls() throws Exception {
        PresignedUrlResponsesDto response = new PresignedUrlResponsesDto(
                List.of(new PresignedUrlSuccessDto(
                        "test.jpg",
                        "image/jpeg",
                        "uploads/pending/1/test.jpg",
                        "https://example.com/upload",
                        Instant.parse("2030-01-01T00:00:00Z")
                ))
        );
        given(fileServiceFacade.issuePutPresignedUrls(any(PresignedUrlRequestDtos.class), eq(1L)))
                .willReturn(response);

        String body = """
                {
                  "presignedUrlRequestDtos": [
                    {
                      "fileName": "test.jpg",
                      "mimeType": "image/jpeg"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/files/upload-presign")
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presignedUrlResponses[0].fileName").value("test.jpg"));

        verify(fileServiceFacade, times(1))
                .issuePutPresignedUrls(any(PresignedUrlRequestDtos.class), eq(1L));
    }

    @Test
    @DisplayName("upload presign without login returns unauthorized")
    void issuePutPresignedUrlsUnauthorized() throws Exception {
        String body = """
                {
                  "presignedUrlRequestDtos": [
                    {
                      "fileName": "test.jpg",
                      "mimeType": "image/jpeg"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/files/upload-presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(fileServiceFacade, never())
                .issuePutPresignedUrls(any(PresignedUrlRequestDtos.class), any(Long.class));
    }

    @Test
    @DisplayName("upload presign invalid body returns bad request")
    void issuePutPresignedUrlsBadRequest() throws Exception {
        String invalidBody = """
                {
                  "presignedUrlRequestDtos": []
                }
                """;

        mockMvc.perform(post("/files/upload-presign")
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(fileServiceFacade, never())
                .issuePutPresignedUrls(any(PresignedUrlRequestDtos.class), any(Long.class));
    }

    @Test
    @DisplayName("upload complete returns result")
    void completeUpload() throws Exception {
        FileUploadCompleteResponsesDto response = new FileUploadCompleteResponsesDto(
                List.of(new UploadSuccess(
                        "uploads/pending/1/test.jpg",
                        "uploads/uploaded/1/test.jpg",
                        "https://triple-dev-s3.s3.ap-northeast-2.amazonaws.com/uploads/uploaded/1/test.jpg"
                ))
        );
        given(fileServiceFacade.completeUploads(any(UploadedKeysRequestDto.class), eq(1L)))
                .willReturn(response);

        String body = """
                {
                  "keys": [
                    "uploads/pending/1/test.jpg"
                  ]
                }
                """;

        mockMvc.perform(post("/files/upload-complete")
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadResults[0].pendingKey").value("uploads/pending/1/test.jpg"));

        verify(fileServiceFacade, times(1))
                .completeUploads(any(UploadedKeysRequestDto.class), eq(1L));
    }

    @Test
    @DisplayName("upload complete without login returns unauthorized")
    void completeUploadUnauthorized() throws Exception {
        String body = """
                {
                  "keys": [
                    "uploads/pending/1/test.jpg"
                  ]
                }
                """;

        mockMvc.perform(post("/files/upload-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(fileServiceFacade, never())
                .completeUploads(any(UploadedKeysRequestDto.class), any(Long.class));
    }

    @Test
    @DisplayName("upload complete invalid body returns bad request")
    void completeUploadBadRequest() throws Exception {
        String invalidBody = """
                {
                  "keys": []
                }
                """;

        mockMvc.perform(post("/files/upload-complete")
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(fileServiceFacade, never())
                .completeUploads(any(UploadedKeysRequestDto.class), any(Long.class));
    }

    private RequestPostProcessor loginJwt() {
        return request -> {
            request.addHeader("Authorization", "Bearer test-token");
            return request;
        };
    }
}
