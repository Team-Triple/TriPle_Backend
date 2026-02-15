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
import org.triple.backend.auth.session.SessionManager;
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

        Object sessionUserIdObj = session.getAttribute(SessionManager.SESSION_KEY);
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
            assertThat(session.getAttribute(SessionManager.SESSION_KEY)).isNull();
        }

        assertThat(userJpaRepository.count()).isEqualTo(0);
    }
}