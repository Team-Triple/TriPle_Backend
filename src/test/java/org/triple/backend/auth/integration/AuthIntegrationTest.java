package org.triple.backend.auth.integration;

import jakarta.servlet.http.Cookie;
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
import org.triple.backend.common.annotation.IntegrationTest;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        userJpaRepository.deleteAll();
        given(oauthClients.get(OauthProvider.KAKAO)).willReturn(kakaoOauthClient);
    }

    @Test
    @DisplayName("kakao login success returns authorization header, refresh cookie and profile")
    void loginSuccess() throws Exception {
        given(kakaoOauthClient.fetchUser(anyString()))
                .willReturn(new OauthUser(
                        OauthProvider.KAKAO,
                        "kakao-1234",
                        "test@test.com",
                        "test",
                        "http://img"
                ));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-code","provider":"KAKAO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Authorization", startsWith("Bearer ")))
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                .andExpect(jsonPath("$.profileUrl").value("http://img"))
                .andExpect(jsonPath("$.nickname").value("test"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andReturn();

        User saved = userJpaRepository.findByProviderAndProviderId(OauthProvider.KAKAO, "kakao-1234")
                .orElseThrow();
        String authorizationHeader = result.getResponse().getHeader("Authorization");
        String setCookie = result.getResponse().getHeader("Set-Cookie");

        assertThat(authorizationHeader).startsWith("Bearer ");
        assertThat(authorizationHeader).isNotBlank();
        assertThat(setCookie).contains("refresh_token=");
        assertThat(saved.getId()).isNotNull();
        assertThat(userJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("refresh success returns new authorization header and rotated refresh cookie")
    void refreshSuccess() throws Exception {
        given(kakaoOauthClient.fetchUser(anyString()))
                .willReturn(new OauthUser(
                        OauthProvider.KAKAO,
                        "kakao-5678",
                        "refresh@test.com",
                        "refresh-user",
                        "http://img"
                ));

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-code","provider":"KAKAO"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = toRefreshCookie(loginResult.getResponse().getHeader("Set-Cookie"));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Authorization", startsWith("Bearer ")))
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")));
    }

    @Test
    @DisplayName("refresh without cookie returns unauthorized")
    void refreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh with stale cookie returns unauthorized")
    void refreshWithStaleCookie() throws Exception {
        given(kakaoOauthClient.fetchUser(anyString()))
                .willReturn(new OauthUser(
                        OauthProvider.KAKAO,
                        "kakao-9999",
                        "stale@test.com",
                        "stale-user",
                        "http://img"
                ));

        MvcResult firstLogin = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-code","provider":"KAKAO"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Cookie staleCookie = toRefreshCookie(firstLogin.getResponse().getHeader("Set-Cookie"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-code","provider":"KAKAO"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh")
                        .cookie(staleCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unsupported provider request does not create user")
    void loginUnsupportedProvider() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-code","provider":"NAVER"}
                                """))
                .andExpect(status().is4xxClientError());

        assertThat(userJpaRepository.count()).isEqualTo(0);
    }

    private Cookie toRefreshCookie(String setCookieHeader) {
        String firstSection = setCookieHeader.split(";", 2)[0];
        String[] cookieParts = firstSection.split("=", 2);
        return new Cookie(cookieParts[0], cookieParts[1]);
    }
}
