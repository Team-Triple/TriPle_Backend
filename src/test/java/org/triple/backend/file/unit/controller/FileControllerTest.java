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
import org.triple.backend.file.dto.response.PresignedUrlResponseDto;
import org.triple.backend.file.dto.response.PresignedUrlResponsesDto;
import org.triple.backend.file.dto.response.UploadResult;
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
    @DisplayName("업로드 Presigned URL 발급 요청 시 발급 결과 목록을 반환한다.")
    void 업로드_Presigned_URL_발급_요청_시_발급_결과_목록을_반환한다() throws Exception {
        // given
        PresignedUrlResponsesDto response = new PresignedUrlResponsesDto(
                List.of(new PresignedUrlResponseDto(
                        "test.jpg",
                        "image/jpeg",
                        "uploads/pending/1/test.jpg",
                        "https://example.com/upload",
                        Instant.parse("2030-01-01T00:00:00Z"),
                        true,
                        null,
                        null
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

        // when & then
        mockMvc.perform(post("/files/upload-presign")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presignedUrlResponseDtos[0].fileName").value("test.jpg"))
                .andExpect(jsonPath("$.presignedUrlResponseDtos[0].mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.presignedUrlResponseDtos[0].key").value("uploads/pending/1/test.jpg"))
                .andExpect(jsonPath("$.presignedUrlResponseDtos[0].presignedUrl").value("https://example.com/upload"))
                .andExpect(jsonPath("$.presignedUrlResponseDtos[0].success").value(true))
                .andDo(document("files/upload-presign",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("presignedUrlRequestDtos").description("presign issue request list"),
                                fieldWithPath("presignedUrlRequestDtos[].fileName").description("original file name"),
                                fieldWithPath("presignedUrlRequestDtos[].mimeType").description("mime type")
                        ),
                        responseFields(
                                fieldWithPath("presignedUrlResponseDtos").description("presign issue response list"),
                                fieldWithPath("presignedUrlResponseDtos[].fileName").description("original file name"),
                                fieldWithPath("presignedUrlResponseDtos[].mimeType").description("mime type"),
                                fieldWithPath("presignedUrlResponseDtos[].key").description("upload target key").optional(),
                                fieldWithPath("presignedUrlResponseDtos[].presignedUrl").description("S3 PUT presigned URL").optional(),
                                fieldWithPath("presignedUrlResponseDtos[].expiresAt").description("presigned URL expires at").optional(),
                                fieldWithPath("presignedUrlResponseDtos[].success").description("success flag"),
                                fieldWithPath("presignedUrlResponseDtos[].errorCode").description("error code").optional(),
                                fieldWithPath("presignedUrlResponseDtos[].message").description("error message").optional()
                        )
                ));

        verify(fileServiceFacade, times(1))
                .issuePutPresignedUrls(any(PresignedUrlRequestDtos.class), eq(1L));
    }

    @Test
    @DisplayName("업로드 완료 요청 시 키별 완료 결과 목록을 반환한다.")
    void 업로드_완료_요청_시_키별_완료_결과_목록을_반환한다() throws Exception {
        // given
        FileUploadCompleteResponsesDto response = new FileUploadCompleteResponsesDto(
                List.of(UploadResult.success(
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

        // when & then
        mockMvc.perform(post("/files/upload-complete")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadResults[0].pendingKey").value("uploads/pending/1/test.jpg"))
                .andExpect(jsonPath("$.uploadResults[0].uploadedKey").value("uploads/uploaded/1/test.jpg"))
                .andExpect(jsonPath("$.uploadResults[0].uploadedUrl").value("https://triple-dev-s3.s3.ap-northeast-2.amazonaws.com/uploads/uploaded/1/test.jpg"))
                .andExpect(jsonPath("$.uploadResults[0].success").value(true))
                .andDo(document("files/upload-complete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("keys").description("업로드 완료 처리할 pending key 목록")
                        ),
                        responseFields(
                                fieldWithPath("uploadResults").description("upload completion result list"),
                                fieldWithPath("uploadResults[].pendingKey").description("requested pending key"),
                                fieldWithPath("uploadResults[].uploadedKey").description("uploaded key").optional(),
                                fieldWithPath("uploadResults[].uploadedUrl").description("public URL for uploaded key").optional(),
                                fieldWithPath("uploadResults[].success").description("success flag"),
                                fieldWithPath("uploadResults[].httpStatus").description("failed HTTP status").optional(),
                                fieldWithPath("uploadResults[].message").description("failed message").optional()
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
