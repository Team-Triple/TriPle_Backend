package org.triple.backend.user.unit.controller;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.user.controller.UserController;
import org.triple.backend.user.dto.request.UpdateUserInfoReq;
import org.triple.backend.user.dto.response.UserInfoResponseDto;
import org.triple.backend.user.entity.Gender;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.service.UserService;

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
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN_KEY;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@WebMvcTest(UserController.class)
class UserControllerTest extends ControllerTest {

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @MockitoBean
    private SessionManager sessionManager;

    @Test
    @DisplayName("사용자 정보 조회를 하면 사용자 정보와 상태코드 200을 반환한다.")
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
        when(sessionManager.getUserIdOrThrow(any())).thenReturn(userId);
        when(csrfTokenManager.isValid(any(), any())).thenReturn(true);

        mockMvc.perform(get("/users/me")
                        .sessionAttr(USER_SESSION_KEY, userId))
                .andDo(document("users/me",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("publicUuid").description("암호화된 사용자 UUID"),
                                fieldWithPath("nickname").description("닉네임"),
                                fieldWithPath("gender").description("성별"),
                                fieldWithPath("birth").description("생일").optional(),
                                fieldWithPath("description").description("소개").optional(),
                                fieldWithPath("profileUrl").description("프로필 이미지 URL").optional()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicUuid").value(encryptedPublicUuid));
    }

    @Test
    @DisplayName("비로그인 사용자가 사용자 정보 조회 요청 시 401을 반환한다.")
    void userInfoReturnsUnauthorizedWithoutLogin() throws Exception {
        when(sessionManager.getUserIdOrThrow(any()))
                .thenThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("users/me-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )));

        verify(userService, never()).userInfo(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자 유저정보 조회 요청 시 404를 반환한다.")
    void userInfoReturnsNotFoundWhenUserMissing() throws Exception {
        Long userId = 1L;
        when(sessionManager.getUserIdOrThrow(any())).thenReturn(userId);
        when(userService.userInfo(userId))
                .thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/users/me")
                        .sessionAttr(USER_SESSION_KEY, userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(UserErrorCode.USER_NOT_FOUND.getMessage()))
                .andDo(document("users/me-fail-user-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )));
    }

    @Test
    @DisplayName("사용자 정보 수정 요청 시 200을 반환한다.")
    void updateUserInfoReturnsOk() throws Exception {
        Long userId = 1L;
        UpdateUserInfoReq request = new UpdateUserInfoReq(
                "sangyun",
                Gender.MALE,
                LocalDate.of(1999, 1, 16),
                "hi",
                "https://example.com/profile.png"
        );

        when(sessionManager.getUserIdOrThrow(any())).thenReturn(userId);
        when(csrfTokenManager.isValid(any(), any())).thenReturn(true);

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
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("users/update",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("nickname").description("닉네임").optional(),
                                fieldWithPath("gender").description("성별").optional(),
                                fieldWithPath("birth").description("생일").optional(),
                                fieldWithPath("description").description("소개").optional(),
                                fieldWithPath("profileUrl").description("프로필 이미지 URL").optional()
                        )));

        verify(userService).updateUserInfo(eq(userId), eq(request));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 정보 수정 요청 시 404를 반환한다.")
    void updateUserInfoReturnsNotFoundWhenUserMissing() throws Exception {
        Long userId = 1L;
        when(sessionManager.getUserIdOrThrow(any())).thenReturn(userId);
        when(csrfTokenManager.isValid(any(), any())).thenReturn(true);
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
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(UserErrorCode.USER_NOT_FOUND.getMessage()))
                .andDo(document("users/update-fail-user-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )));
    }

    private RequestPostProcessor loginSessionAndCsrf() {
        return request -> {
            request.getSession(true).setAttribute(USER_SESSION_KEY, 1L);
            request.getSession().setAttribute(CSRF_TOKEN_KEY, CSRF_TOKEN);
            request.addHeader(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN);
            return request;
        };
    }
}
