package org.triple.backend.auth.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.triple.backend.auth.controller.AuthController;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.service.AuthServiceFacade;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.global.error.BusinessException;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends ControllerTest {

    @MockitoBean
    private AuthServiceFacade authServiceFacade;

    @Test
    @DisplayName("login success returns profile response, authorization header and refresh cookie")
    void loginSuccess() throws Exception {
        given(authServiceFacade.login(any(AuthLoginRequestDto.class), any(HttpServletResponse.class)))
                .willAnswer(invocation -> {
                    HttpServletResponse response = invocation.getArgument(1, HttpServletResponse.class);
                    response.setHeader("Authorization", "Bearer access-token");
                    response.addHeader("Set-Cookie", "refresh_token=refresh-token; Path=/auth; HttpOnly");
                    return new AuthLoginResponseDto("test", "test@test.com", "http://img");
                });

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-code","provider":"KAKAO"}
                                """))
                .andDo(document("auth/login",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())))
                .andExpect(status().isOk())
                .andExpect(header().string("Authorization", startsWith("Bearer ")))
                .andExpect(header().string("Set-Cookie", startsWith("refresh_token=")))
                .andExpect(jsonPath("$.nickname").value("test"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.profileUrl").value("http://img"));

        verify(authServiceFacade, times(1)).login(any(AuthLoginRequestDto.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("invalid login request returns bad request")
    void invalidLoginRequest() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"","provider":null}
                                """))
                .andDo(document("auth/login-fail-bad-request",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(authServiceFacade, never()).login(any(AuthLoginRequestDto.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("unsupported oauth provider returns unauthorized")
    void loginUnsupportedProvider() throws Exception {
        given(authServiceFacade.login(any(AuthLoginRequestDto.class), any(HttpServletResponse.class)))
                .willThrow(new BusinessException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-code","provider":"GOOGLE"}
                                """))
                .andDo(document("auth/login-fail-unsupported-provider",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("kakao access token issue failure returns unauthorized")
    void loginFailIssueKakaoAccessToken() throws Exception {
        given(authServiceFacade.login(any(AuthLoginRequestDto.class), any(HttpServletResponse.class)))
                .willThrow(new BusinessException(AuthErrorCode.FAILED_ISSUE_KAKAO_ACCESS_TOKEN));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-code","provider":"KAKAO"}
                                """))
                .andDo(document("auth/login-fail-issue-kakao-access-token",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(AuthErrorCode.FAILED_ISSUE_KAKAO_ACCESS_TOKEN.getMessage()));
    }

    @Test
    @DisplayName("kakao user info fetch failure returns unauthorized")
    void loginFailFindKakaoUserInfo() throws Exception {
        given(authServiceFacade.login(any(AuthLoginRequestDto.class), any(HttpServletResponse.class)))
                .willThrow(new BusinessException(AuthErrorCode.FAILED_FIND_KAKAO_USER_INFO));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-code","provider":"KAKAO"}
                                """))
                .andDo(document("auth/login-fail-find-kakao-user-info",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(AuthErrorCode.FAILED_FIND_KAKAO_USER_INFO.getMessage()));
    }

    @Test
    @DisplayName("refresh success returns authorization header and rotated refresh cookie")
    void refreshSuccess() throws Exception {
        org.mockito.BDDMockito.willAnswer(invocation -> {
                    HttpServletResponse response = invocation.getArgument(1, HttpServletResponse.class);
                    response.setHeader("Authorization", "Bearer new-access-token");
                    response.addHeader("Set-Cookie", "refresh_token=new-refresh-token; Path=/auth; HttpOnly");
                    return null;
                })
                .given(authServiceFacade)
                .refresh(any(HttpServletRequest.class), any(HttpServletResponse.class));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andDo(document("auth/refresh",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())))
                .andExpect(status().isOk())
                .andExpect(header().string("Authorization", startsWith("Bearer ")))
                .andExpect(header().string("Set-Cookie", startsWith("refresh_token=")));

        verify(authServiceFacade, times(1)).refresh(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("refresh without cookie returns unauthorized")
    void refreshWithoutCookie() throws Exception {
        org.mockito.BDDMockito.willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED))
                .given(authServiceFacade)
                .refresh(any(HttpServletRequest.class), any(HttpServletResponse.class));

        mockMvc.perform(post("/auth/refresh"))
                .andDo(document("auth/refresh-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())))
                .andExpect(status().isUnauthorized());
    }
}
