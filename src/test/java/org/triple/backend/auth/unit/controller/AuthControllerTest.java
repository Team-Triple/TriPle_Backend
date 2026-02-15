package org.triple.backend.auth.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.triple.backend.auth.controller.AuthController;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.service.AuthService;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.common.ControllerTest;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
public class AuthControllerTest extends ControllerTest {

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SessionManager sessionManager;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Oauth 로그인을 성공하면, 사용자 아이디와 200을 반환한다.")
    void Oauth_로그인을_성공하면_사용자_아이디를_반환한다() throws Exception {
        // given
        String code = "인가 코드";
        AuthLoginRequestDto req = new AuthLoginRequestDto(code, OauthProvider.KAKAO);

        when(authService.login(eq(req), any(HttpServletRequest.class)))
                .thenReturn(new AuthLoginResponseDto("test", "test@test.com","https://test.png"));
        when(csrfTokenManager.getOrCreateToken(any(HttpServletRequest.class)))
                .thenReturn("csrf-token");

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(document("auth/login",
                        requestFields(
                                fieldWithPath("code").description("카카오 인가 코드"),
                                fieldWithPath("provider").description("OAuth Provider")
                        ),
                        responseFields(
                                fieldWithPath("nickname").description("로그인된 사용자 ID"),
                                fieldWithPath("email").description("로그인된 사용자 이메일"),
                                fieldWithPath("profileUrl").description("로그인된 사용자 프로필 URL")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("test"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.profileUrl").value("https://test.png"));
    }
}
