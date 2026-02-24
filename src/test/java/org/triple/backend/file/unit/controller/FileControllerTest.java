package org.triple.backend.file.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.session.CsrfTokenManager;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.JsonFieldType.ARRAY;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
class FileControllerTest extends ControllerTest {

    private static final String USER_SESSION_KEY = "USER_ID";
    private static final String CSRF_TOKEN = "csrf-token";

    @MockitoBean
    private FileServiceFacade fileServiceFacade;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @Test
    @DisplayName("upload presigned urls returns list")
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
        mockCsrfValid();

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
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presignedUrlResponses[0].fileName").value("test.jpg"))
                .andExpect(jsonPath("$.presignedUrlResponses[0].mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.presignedUrlResponses[0].key").value("uploads/pending/1/test.jpg"))
                .andExpect(jsonPath("$.presignedUrlResponses[0].presignedUrl").value("https://example.com/upload"))
                .andDo(document("files/upload-presign",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("presignedUrlRequestDtos").type(ARRAY).description("presign issue request list"),
                                fieldWithPath("presignedUrlRequestDtos[].fileName").type(STRING).description("original file name"),
                                fieldWithPath("presignedUrlRequestDtos[].mimeType").type(STRING).description("mime type")
                        ),
                        responseFields(
                                fieldWithPath("presignedUrlResponses").type(ARRAY).description("presign issue response list"),
                                fieldWithPath("presignedUrlResponses[].fileName").type(STRING).description("original file name"),
                                fieldWithPath("presignedUrlResponses[].mimeType").type(STRING).description("mime type"),
                                fieldWithPath("presignedUrlResponses[].key").type(STRING).description("upload target key").optional(),
                                fieldWithPath("presignedUrlResponses[].presignedUrl").type(STRING).description("S3 PUT presigned URL").optional(),
                                fieldWithPath("presignedUrlResponses[].expiresAt").type(STRING).description("presigned URL expires at").optional(),
                                fieldWithPath("presignedUrlResponses[].errorCode").type(STRING).description("error code").optional(),
                                fieldWithPath("presignedUrlResponses[].message").type(STRING).description("error message").optional()
                        )
                ));

        verify(fileServiceFacade, times(1))
                .issuePutPresignedUrls(any(PresignedUrlRequestDtos.class), eq(1L));
    }

    @Test
    @DisplayName("complete upload returns per-key result")
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
        mockCsrfValid();

        String body = """
                {
                  "keys": [
                    "uploads/pending/1/test.jpg"
                  ]
                }
                """;

        mockMvc.perform(post("/files/upload-complete")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadResults[0].pendingKey").value("uploads/pending/1/test.jpg"))
                .andExpect(jsonPath("$.uploadResults[0].uploadedKey").value("uploads/uploaded/1/test.jpg"))
                .andExpect(jsonPath("$.uploadResults[0].uploadedUrl").value("https://triple-dev-s3.s3.ap-northeast-2.amazonaws.com/uploads/uploaded/1/test.jpg"))
                .andDo(document("files/upload-complete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("keys").type(ARRAY).description("pending key list")
                        ),
                        responseFields(
                                fieldWithPath("uploadResults").type(ARRAY).description("upload completion result list"),
                                fieldWithPath("uploadResults[].pendingKey").type(STRING).description("requested pending key"),
                                fieldWithPath("uploadResults[].uploadedKey").type(STRING).description("uploaded key").optional(),
                                fieldWithPath("uploadResults[].uploadedUrl").type(STRING).description("public URL for uploaded key").optional(),
                                fieldWithPath("uploadResults[].httpStatus").type(STRING).description("failed HTTP status").optional(),
                                fieldWithPath("uploadResults[].message").type(STRING).description("failed message").optional()
                        )
                ));

        verify(fileServiceFacade, times(1))
                .completeUploads(any(UploadedKeysRequestDto.class), eq(1L));
    }

    private void mockCsrfValid() {
        when(csrfTokenManager.isValid(any(HttpServletRequest.class), any(String.class))).thenReturn(true);
    }

    private RequestPostProcessor loginSessionAndCsrf() {
        return request -> {
            request.getSession(true).setAttribute(USER_SESSION_KEY, 1L);
            request.getSession().setAttribute(CsrfTokenManager.CSRF_TOKEN_KEY, CSRF_TOKEN);
            request.addHeader(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN);
            return request;
        };
    }
}
