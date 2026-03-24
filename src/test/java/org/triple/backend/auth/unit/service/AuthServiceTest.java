package org.triple.backend.auth.unit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.triple.backend.auth.config.property.JwtProperties;
import org.triple.backend.auth.crypto.UuidToUserIdCache;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.jwt.JwtManager;
import org.triple.backend.auth.oauth.OauthClient;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.oauth.OauthUser;
import org.triple.backend.auth.service.AuthService;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ServiceTest
@Import({AuthService.class, JwtManager.class, AuthServiceTest.JwtTestConfig.class})
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtManager jwtManager;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @MockitoBean
    private UuidToUserIdCache uuidToUserIdCache;

    @MockitoBean
    private Map<OauthProvider, OauthClient> clients;

    @MockitoBean
    private OauthClient kakaoClient;

    @BeforeEach
    void setUp() {
        userJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("unsupported provider returns UNSUPPORTED_OAUTH_PROVIDER")
    void unsupportedProvider() {
        AuthLoginRequestDto request = new AuthLoginRequestDto("code", OauthProvider.KAKAO);
        given(clients.get(OauthProvider.KAKAO)).willReturn(null);

        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    }

    @Test
    @DisplayName("authenticate fetches oauth user from client")
    void authenticateSuccess() {
        AuthLoginRequestDto request = new AuthLoginRequestDto("code", OauthProvider.KAKAO);
        OauthUser oauthUser = new OauthUser(
                OauthProvider.KAKAO,
                "kakao-999",
                "new@test.com",
                "newbie",
                "http://img"
        );
        given(clients.get(OauthProvider.KAKAO)).willReturn(kakaoClient);
        given(kakaoClient.fetchUser("code")).willReturn(oauthUser);

        OauthUser result = authService.authenticate(request);

        assertThat(result).isEqualTo(oauthUser);
    }

    @Test
    @DisplayName("findOrCreate creates user and sets authorization header")
    void findOrCreateCreatesUserAndSetsJwtHeader() {
        OauthUser oauthUser = new OauthUser(
                OauthProvider.KAKAO,
                "kakao-999",
                "new@test.com",
                "newbie",
                "http://img"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthLoginResponseDto result = authService.findOrCreate(oauthUser, response);

        User saved = userJpaRepository.findByProviderAndProviderId(OauthProvider.KAKAO, "kakao-999")
                .orElseThrow();
        String authorizationHeader = response.getHeader("Authorization");

        assertThat(result.nickname()).isEqualTo("newbie");
        assertThat(result.email()).isEqualTo("new@test.com");
        assertThat(result.profileUrl()).isEqualTo("http://img");
        assertThat(authorizationHeader).startsWith("Bearer ");
        assertThat(jwtManager.resolveUserId(authorizationHeader)).isEqualTo(saved.getId());
        assertThat(userJpaRepository.count()).isEqualTo(1);
        verify(uuidToUserIdCache).save(saved.getPublicUuid(), saved.getId());
    }

    @Test
    @DisplayName("findOrCreate reuses existing user and does not duplicate")
    void findOrCreateExistingUser() {
        User existing = userJpaRepository.save(User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId("kakao-123")
                .email("test@test.com")
                .nickname("nick")
                .profileUrl("http://img")
                .build());

        OauthUser oauthUser = new OauthUser(
                OauthProvider.KAKAO,
                "kakao-123",
                "test@test.com",
                "nick",
                "http://img"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthLoginResponseDto result = authService.findOrCreate(oauthUser, response);

        User found = userJpaRepository.findByProviderAndProviderId(OauthProvider.KAKAO, "kakao-123")
                .orElseThrow();
        String authorizationHeader = response.getHeader("Authorization");

        assertThat(userJpaRepository.count()).isEqualTo(1);
        assertThat(found.getId()).isEqualTo(existing.getId());
        assertThat(result.nickname()).isEqualTo(existing.getNickname());
        assertThat(authorizationHeader).startsWith("Bearer ");
        assertThat(jwtManager.resolveUserId(authorizationHeader)).isEqualTo(existing.getId());
        verify(uuidToUserIdCache).save(found.getPublicUuid(), existing.getId());
    }

    @TestConfiguration
    static class JwtTestConfig {
        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties("test-jwt-secret-value-at-least-32-characters", 3600);
        }
    }
}
