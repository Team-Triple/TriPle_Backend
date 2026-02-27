package org.triple.backend.auth.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.triple.backend.auth.controller.AuthController;
import org.triple.backend.auth.cookie.CookieManager;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.service.AuthService;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.common.ControllerTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN_KEY;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@WebMvcTest(AuthController.class)
public class AuthControllerTest extends ControllerTest {

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SessionManager sessionManager;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @Autowired
    private CookieManager cookieManager;

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
                .thenReturn(CSRF_TOKEN);

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

    @Test
    @DisplayName("로그인 성공 시 응답 바디 + CSRF 토큰 + 로그인 쿠키를 전달합니다")
    void 로그인_성공_시_응답_바디값_CSRF_토큰_로그인_쿠키를_전달합니다() throws Exception {
        // given
        String body = """
                {"code":"test-code","provider":"KAKAO"}
                """;

        given(authService.login(any(AuthLoginRequestDto.class), any(HttpServletRequest.class)))
                .willReturn(new AuthLoginResponseDto("test", "test@test.com", "http://img"));

        given(csrfTokenManager.getOrCreateToken(any(HttpServletRequest.class)))
                .willReturn("csrf-token-123");

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("test"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.profileUrl").value("http://img"))
                .andExpect(header().string(CsrfTokenManager.CSRF_HEADER, "csrf-token-123"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                            containsString("login_status=true"),
                            containsString("Secure"),
                            containsString("SameSite=None"),
                            not(containsString("HttpOnly"))
                    )));

        verify(csrfTokenManager, times(1)).getOrCreateToken(any(HttpServletRequest.class));
        verify(authService, times(1)).login(any(AuthLoginRequestDto.class), any(HttpServletRequest.class));
    }

    @Test
    @DisplayName("로그아웃 시 세션 무효화와 login_status/JSESSIONID 쿠키 만료를 수행한다")
    void 로그아웃_시_세션_무효화와_쿠키_만료를_수행한다() throws Exception {
        // given
        given(csrfTokenManager.isValid(any(HttpServletRequest.class), eq(CSRF_TOKEN)))
                .willReturn(true);

        // when
        var result = mockMvc.perform(post("/auth/logout")
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN)
                        .sessionAttr(USER_SESSION_KEY, 1L)
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andDo(document("auth/logout",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestHeaders(
                                headerWithName(CsrfTokenManager.CSRF_HEADER)
                                        .description("로그인 상태에서 로그아웃 시 필요한 CSRF 토큰")
                        ),
                        responseHeaders(
                                headerWithName(HttpHeaders.SET_COOKIE)
                                        .description("만료 쿠키. login_status와 JSESSIONID가 각각 Max-Age=0으로 내려갑니다.")
                        )))
                .andReturn();

        // then
        verify(authService, times(1)).logout(any(HttpServletRequest.class));

        var setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies)
                .anySatisfy(cookie -> assertThat(cookie).contains("login_status=").contains("Max-Age=0"))
                .anySatisfy(cookie -> assertThat(cookie).contains("JSESSIONID=").contains("Max-Age=0"));
    }

    @Test
    @DisplayName("로그인 세션이 있고 CSRF 토큰이 유효하지 않으면 로그아웃은 403을 반환한다")
    void 로그인_세션이_있고_CSRF_토큰이_유효하지_않으면_로그아웃은_403을_반환한다() throws Exception {
        // given
        given(csrfTokenManager.isValid(any(HttpServletRequest.class), any()))
                .willReturn(false);

        // when & then
        mockMvc.perform(post("/auth/logout")
                        .sessionAttr(USER_SESSION_KEY, 1L)
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN))
                .andExpect(status().isForbidden());

        verify(authService, never()).logout(any(HttpServletRequest.class));
    }
}
