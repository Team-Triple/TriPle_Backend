package org.triple.backend.auth.integration;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.triple.backend.auth.oauth.OauthClient;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.oauth.OauthUser;
import org.triple.backend.auth.oauth.kakao.KakaoOauthClient;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.common.annotation.IntegrationTest;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN_KEY;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@IntegrationTest
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @MockitoBean
    private KakaoOauthClient kakaoOauthClient;

    @MockitoBean(name = "oauthClients")
    private Map<OauthProvider, OauthClient> oauthClients;

    @BeforeEach
    void setUp() {
        given(oauthClients.get(OauthProvider.KAKAO)).willReturn(kakaoOauthClient);
    }

    @Test
    @DisplayName("카카오 로그인을 성공합니다.")
    void 카카오_로그인_성공합니다() throws Exception {
        given(kakaoOauthClient.fetchUser(anyString()))
                .willReturn(new OauthUser(
                        OauthProvider.KAKAO,
                        "kakao-1234",
                        "test@test.com",
                        "test",
                        "http://img"
                ));

        String body = """
                {"code":"test-code","provider":"KAKAO"}
                """;


        // when
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileUrl").value("http://img"))
                .andExpect(jsonPath("$.nickname").value("test"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andReturn();

        // then

        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        Object sessionUserIdObj = session.getAttribute(USER_SESSION_KEY);
        assertThat(sessionUserIdObj).isNotNull();

        User saved = userJpaRepository.findByProviderAndProviderId(OauthProvider.KAKAO, "kakao-1234")
                .orElseThrow();

        assertThat(saved.getProvider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(saved.getProviderId()).isEqualTo("kakao-1234");
        assertThat(saved.getEmail()).isEqualTo("test@test.com");
        assertThat(saved.getNickname()).isEqualTo("test");
        assertThat(saved.getProfileUrl()).isEqualTo("http://img");

        assertThat(userJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("지원하지 않는 provider면 요청이 실패하고 세션과 DB에 사용자가 생성되지 않습니다.")
    void 지원하지_않는_provider면_요청이_실패하고_세션과_DB에_사용자가_생성되지_않습니다() throws Exception {
        // given
        String body = """
            {"code":"test-code","provider":"NAVER"}
            """;

        // when
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError())
                .andReturn();

        // then
        HttpSession session = result.getRequest().getSession(false);
        if (session != null) {
            assertThat(session.getAttribute(USER_SESSION_KEY)).isNull();
        }

        assertThat(userJpaRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("로그아웃 성공 시 세션을 무효화하고 login_status/JSESSIONID 쿠키를 만료시킨다")
    void 로그아웃_성공_시_세션을_무효화하고_쿠키를_만료시킨다() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/auth/logout")
                        .sessionAttr(USER_SESSION_KEY, 1L)
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk())
                .andReturn();

        // then
        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isNull();

        var setCookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookies)
                .anySatisfy(cookie -> assertThat(cookie).contains("login_status=").contains("Max-Age=0"))
                .anySatisfy(cookie -> assertThat(cookie).contains("JSESSIONID=").contains("Max-Age=0"));
    }

    @Test
    @DisplayName("로그인 세션이 있고 CSRF 토큰이 없으면 로그아웃은 403을 반환한다")
    void 로그인_세션이_있고_CSRF_토큰이_없으면_로그아웃은_403을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .sessionAttr(USER_SESSION_KEY, 1L)
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN))
                .andExpect(status().isForbidden());
    }
}
