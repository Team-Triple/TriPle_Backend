package org.triple.backend.auth.unit.controller;

import jakarta.servlet.http.HttpServletResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends ControllerTest {

    @MockitoBean
    private AuthServiceFacade authServiceFacade;

    @Test
    @DisplayName("login success returns profile response and authorization header")
    void loginSuccess() throws Exception {
        given(authServiceFacade.login(any(AuthLoginRequestDto.class), any(HttpServletResponse.class)))
                .willAnswer(invocation -> {
                    HttpServletResponse response = invocation.getArgument(1, HttpServletResponse.class);
                    response.setHeader("Authorization", "Bearer access-token");
                    return new AuthLoginResponseDto("test", "test@test.com", "http://img");
                });

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-code","provider":"KAKAO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Authorization", startsWith("Bearer ")))
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
                .andExpect(status().isUnauthorized());
    }
}
