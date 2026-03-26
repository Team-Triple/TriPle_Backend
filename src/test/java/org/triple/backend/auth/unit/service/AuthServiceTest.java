package org.triple.backend.auth.unit.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.triple.backend.auth.config.property.JwtProperties;
import org.triple.backend.auth.crypto.UuidToUserIdCache;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.entity.RefreshToken;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.jwt.JwtCookieWriter;
import org.triple.backend.auth.jwt.JwtManager;
import org.triple.backend.auth.oauth.OauthClient;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.oauth.OauthUser;
import org.triple.backend.auth.repository.RefreshTokenJpaRepository;
import org.triple.backend.auth.service.AuthService;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ServiceTest
@Import({AuthService.class, JwtManager.class, JwtCookieWriter.class, AuthServiceTest.JwtTestConfig.class})
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtManager jwtManager;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @MockitoBean
    private UuidToUserIdCache uuidToUserIdCache;

    @MockitoBean
    private Map<OauthProvider, OauthClient> clients;

    @MockitoBean
    private OauthClient kakaoClient;

    @BeforeEach
    void setUp() {
        refreshTokenJpaRepository.deleteAll();
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
    @DisplayName("findOrCreate creates user and returns access header and refresh cookie")
    void findOrCreateCreatesUserAndReturnsTokens() {
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
        RefreshToken savedRefreshToken = refreshTokenJpaRepository.findTopByUserIdOrderByIdDesc(saved.getId())
                .orElseThrow();
        String authorizationHeader = response.getHeader("Authorization");
        String cookieHeader = response.getHeader("Set-Cookie");
        String refreshToken = extractRefreshToken(cookieHeader);

        assertThat(result.nickname()).isEqualTo("newbie");
        assertThat(result.email()).isEqualTo("new@test.com");
        assertThat(result.profileUrl()).isEqualTo("http://img");
        assertThat(authorizationHeader).startsWith("Bearer ");
        assertThat(jwtManager.resolveUserId(authorizationHeader)).isEqualTo(saved.getId());
        assertThat(cookieHeader).startsWith("refresh_token=");
        assertThat(cookieHeader).contains("HttpOnly");
        assertThat(cookieHeader).contains("Path=/auth");
        assertThat(jwtManager.resolveUserIdFromRefreshToken(refreshToken)).isEqualTo(saved.getId());
        assertThat(savedRefreshToken.getTokenHash()).isEqualTo(jwtManager.hashToken(refreshToken));
        assertThat(savedRefreshToken.getExpiresAt()).isAfter(LocalDateTime.now().minusSeconds(1));
        assertThat(userJpaRepository.count()).isEqualTo(1);
        assertThat(refreshTokenJpaRepository.count()).isEqualTo(1);
        verify(uuidToUserIdCache).save(saved.getPublicUuid(), saved.getId());
    }

    @Test
    @DisplayName("findOrCreate reuses existing user and rotates refresh token row")
    void findOrCreateExistingUser() {
        User existing = userJpaRepository.save(User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId("kakao-123")
                .email("test@test.com")
                .nickname("nick")
                .profileUrl("http://img")
                .build());
        refreshTokenJpaRepository.save(
                RefreshToken.create(existing.getId(), "old-token-hash", LocalDateTime.now().plusHours(1))
        );

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
        RefreshToken savedRefreshToken = refreshTokenJpaRepository.findTopByUserIdOrderByIdDesc(found.getId())
                .orElseThrow();
        String authorizationHeader = response.getHeader("Authorization");
        String cookieHeader = response.getHeader("Set-Cookie");
        String refreshToken = extractRefreshToken(cookieHeader);

        assertThat(userJpaRepository.count()).isEqualTo(1);
        assertThat(refreshTokenJpaRepository.count()).isEqualTo(1);
        assertThat(found.getId()).isEqualTo(existing.getId());
        assertThat(result.nickname()).isEqualTo(existing.getNickname());
        assertThat(authorizationHeader).startsWith("Bearer ");
        assertThat(jwtManager.resolveUserId(authorizationHeader)).isEqualTo(existing.getId());
        assertThat(savedRefreshToken.getTokenHash()).isEqualTo(jwtManager.hashToken(refreshToken));
        assertThat(savedRefreshToken.getTokenHash()).isNotEqualTo("old-token-hash");
        verify(uuidToUserIdCache).save(found.getPublicUuid(), existing.getId());
    }

    @Test
    @DisplayName("reissueAccessToken validates cookie and rotates refresh token")
    void reissueAccessTokenSuccess() {
        OauthUser oauthUser = new OauthUser(
                OauthProvider.KAKAO,
                "kakao-777",
                "refresh@test.com",
                "refresh-user",
                "http://img"
        );
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        authService.findOrCreate(oauthUser, loginResponse);

        User saved = userJpaRepository.findByProviderAndProviderId(OauthProvider.KAKAO, "kakao-777")
                .orElseThrow();
        String oldRefreshToken = extractRefreshToken(loginResponse.getHeader("Set-Cookie"));

        MockHttpServletRequest refreshRequest = new MockHttpServletRequest();
        refreshRequest.setCookies(new Cookie("refresh_token", oldRefreshToken));
        MockHttpServletResponse refreshResponse = new MockHttpServletResponse();

        authService.reissueAccessToken(refreshRequest, refreshResponse);

        String refreshedAuthorizationHeader = refreshResponse.getHeader("Authorization");
        String refreshedCookieHeader = refreshResponse.getHeader("Set-Cookie");
        String newRefreshToken = extractRefreshToken(refreshedCookieHeader);
        RefreshToken refreshed = refreshTokenJpaRepository.findTopByUserIdOrderByIdDesc(saved.getId())
                .orElseThrow();

        assertThat(refreshedAuthorizationHeader).startsWith("Bearer ");
        assertThat(jwtManager.resolveUserId(refreshedAuthorizationHeader)).isEqualTo(saved.getId());
        assertThat(refreshedCookieHeader).startsWith("refresh_token=");
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
        assertThat(jwtManager.resolveUserIdFromRefreshToken(newRefreshToken)).isEqualTo(saved.getId());
        assertThat(refreshed.getTokenHash()).isEqualTo(jwtManager.hashToken(newRefreshToken));
        assertThat(refreshTokenJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("reissueAccessToken without refresh cookie throws unauthorized")
    void reissueAccessTokenWithoutCookie() {
        MockHttpServletRequest refreshRequest = new MockHttpServletRequest();
        MockHttpServletResponse refreshResponse = new MockHttpServletResponse();

        assertThatThrownBy(() -> authService.reissueAccessToken(refreshRequest, refreshResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("reissueAccessToken with expired db token throws unauthorized")
    void reissueAccessTokenWithExpiredDbToken() {
        OauthUser oauthUser = new OauthUser(
                OauthProvider.KAKAO,
                "kakao-888",
                "expired@test.com",
                "expired-user",
                "http://img"
        );
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        authService.findOrCreate(oauthUser, loginResponse);

        User saved = userJpaRepository.findByProviderAndProviderId(OauthProvider.KAKAO, "kakao-888")
                .orElseThrow();
        RefreshToken stored = refreshTokenJpaRepository.findTopByUserIdOrderByIdDesc(saved.getId())
                .orElseThrow();
        stored.rotate(stored.getTokenHash(), LocalDateTime.now().minusSeconds(1));
        refreshTokenJpaRepository.save(stored);

        String refreshToken = extractRefreshToken(loginResponse.getHeader("Set-Cookie"));
        MockHttpServletRequest refreshRequest = new MockHttpServletRequest();
        refreshRequest.setCookies(new Cookie("refresh_token", refreshToken));
        MockHttpServletResponse refreshResponse = new MockHttpServletResponse();

        assertThatThrownBy(() -> authService.reissueAccessToken(refreshRequest, refreshResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("reissueAccessToken with mismatched db hash throws unauthorized")
    void reissueAccessTokenWithMismatchedDbHash() {
        OauthUser oauthUser = new OauthUser(
                OauthProvider.KAKAO,
                "kakao-889",
                "mismatch@test.com",
                "mismatch-user",
                "http://img"
        );
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        authService.findOrCreate(oauthUser, loginResponse);

        User saved = userJpaRepository.findByProviderAndProviderId(OauthProvider.KAKAO, "kakao-889")
                .orElseThrow();
        RefreshToken stored = refreshTokenJpaRepository.findTopByUserIdOrderByIdDesc(saved.getId())
                .orElseThrow();
        stored.rotate("different-token-hash", LocalDateTime.now().plusHours(1));
        refreshTokenJpaRepository.save(stored);

        String refreshToken = extractRefreshToken(loginResponse.getHeader("Set-Cookie"));
        MockHttpServletRequest refreshRequest = new MockHttpServletRequest();
        refreshRequest.setCookies(new Cookie("refresh_token", refreshToken));
        MockHttpServletResponse refreshResponse = new MockHttpServletResponse();

        assertThatThrownBy(() -> authService.reissueAccessToken(refreshRequest, refreshResponse))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    private String extractRefreshToken(String cookieHeader) {
        String firstSection = cookieHeader.split(";", 2)[0];
        String[] tokenParts = firstSection.split("=", 2);
        return tokenParts[1];
    }

    @TestConfiguration
    static class JwtTestConfig {
        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "test-jwt-secret-value-at-least-32-characters",
                    3600,
                    1209600L,
                    "refresh_token",
                    "/auth",
                    false,
                    "Lax"
            );
        }
    }
}
