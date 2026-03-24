package org.triple.backend.user.unit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.user.controller.UserController;
import org.triple.backend.user.dto.request.UpdateUserInfoReq;
import org.triple.backend.user.dto.response.UserInfoResponseDto;
import org.triple.backend.user.entity.Gender;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.service.UserService;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest extends ControllerTest {

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("user info returns 200")
    void userInfoReturnsUserInfo() throws Exception {
        Long userId = 1L;
        String encryptedPublicUuid = "encrypted-public-uuid-3";

        when(userService.userInfo(userId))
                .thenReturn(UserInfoResponseDto.builder()
                        .publicUuid(encryptedPublicUuid)
                        .nickname("sangyun")
                        .gender("MALE")
                        .birth(LocalDate.of(1999, 1, 16))
                        .description("hi")
                        .profileUrl("https://example.com/profile.png")
                        .build());

        mockMvc.perform(get("/users/me").with(loginJwt()))
                .andDo(document("users/me",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("publicUuid").description("encrypted user uuid"),
                                fieldWithPath("nickname").description("nickname"),
                                fieldWithPath("gender").description("gender"),
                                fieldWithPath("birth").description("birth date").optional(),
                                fieldWithPath("description").description("description").optional(),
                                fieldWithPath("profileUrl").description("profile image URL").optional()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicUuid").value(encryptedPublicUuid));

        verify(userService).userInfo(userId);
    }

    @Test
    @DisplayName("user info without login returns 401")
    void userInfoReturnsUnauthorizedWithoutLogin() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(AuthErrorCode.UNAUTHORIZED.getMessage()))
                .andDo(document("users/me-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("message").description("error message")
                        )));

        verify(userService, never()).userInfo(any());
    }

    @Test
    @DisplayName("user info missing user returns 404")
    void userInfoReturnsNotFoundWhenUserMissing() throws Exception {
        Long userId = 1L;
        when(userService.userInfo(userId))
                .thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/users/me").with(loginJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(UserErrorCode.USER_NOT_FOUND.getMessage()))
                .andDo(document("users/me-fail-user-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("message").description("error message")
                        )));
    }

    @Test
    @DisplayName("update user info returns 200")
    void updateUserInfoReturnsOk() throws Exception {
        Long userId = 1L;
        UpdateUserInfoReq request = new UpdateUserInfoReq(
                "sangyun",
                Gender.MALE,
                LocalDate.of(1999, 1, 16),
                "hi",
                "https://example.com/profile.png"
        );

        mockMvc.perform(patch("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "sangyun",
                                  "gender": "MALE",
                                  "birth": "1999-01-16",
                                  "description": "hi",
                                  "profileUrl": "https://example.com/profile.png"
                                }
                                """)
                        .with(loginJwt()))
                .andExpect(status().isOk())
                .andDo(document("users/update",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("nickname").description("nickname").optional(),
                                fieldWithPath("gender").description("gender").optional(),
                                fieldWithPath("birth").description("birth date").optional(),
                                fieldWithPath("description").description("description").optional(),
                                fieldWithPath("profileUrl").description("profile image URL").optional()
                        )));

        verify(userService).updateUserInfo(eq(userId), eq(request));
    }

    @Test
    @DisplayName("update user info missing user returns 404")
    void updateUserInfoReturnsNotFoundWhenUserMissing() throws Exception {
        Long userId = 1L;
        doThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .when(userService)
                .updateUserInfo(eq(userId), any(UpdateUserInfoReq.class));

        mockMvc.perform(patch("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "sangyun"
                                }
                                """)
                        .with(loginJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(UserErrorCode.USER_NOT_FOUND.getMessage()))
                .andDo(document("users/update-fail-user-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("message").description("error message")
                        )));
    }

    private RequestPostProcessor loginJwt() {
        return request -> {
            request.addHeader("Authorization", "Bearer test-token");
            return request;
        };
    }
}
