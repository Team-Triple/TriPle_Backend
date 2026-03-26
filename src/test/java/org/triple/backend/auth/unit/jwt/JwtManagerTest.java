package org.triple.backend.auth.unit.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.auth.config.property.JwtProperties;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.jwt.JwtManager;
import org.triple.backend.global.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtManagerTest {

    private final JwtManager jwtManager = new JwtManager(
            new JwtProperties(
                    "test-jwt-secret-value-at-least-32-characters",
                    3600,
                    1209600L,
                    "refresh_token",
                    "/auth",
                    false,
                    "Lax"
            )
    );

    @Test
    @DisplayName("creates access token and resolves user id from authorization header")
    void createAndParseAccessTokenSuccess() {
        String token = jwtManager.createAccessToken(1L);

        Long userId = jwtManager.resolveUserId("Bearer " + token);

        assertThat(userId).isEqualTo(1L);
    }

    @Test
    @DisplayName("returns null when authorization header is missing")
    void missingHeaderReturnsNull() {
        Long userId = jwtManager.resolveUserId(null);

        assertThat(userId).isNull();
    }

    @Test
    @DisplayName("throws unauthorized for invalid access token")
    void invalidAccessTokenThrowsUnauthorized() {
        assertThatThrownBy(() -> jwtManager.resolveUserId("Bearer invalid-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("creates refresh token and resolves user id")
    void createAndParseRefreshTokenSuccess() {
        String refreshToken = jwtManager.createRefreshToken(11L);

        Long userId = jwtManager.resolveUserIdFromRefreshToken(refreshToken);

        assertThat(userId).isEqualTo(11L);
    }

    @Test
    @DisplayName("throws unauthorized when access token is used as refresh token")
    void accessTokenUsedAsRefreshTokenThrowsUnauthorized() {
        String accessToken = jwtManager.createAccessToken(1L);

        assertThatThrownBy(() -> jwtManager.resolveUserIdFromRefreshToken(accessToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("throws unauthorized when refresh token is used as access token")
    void refreshTokenUsedAsAccessTokenThrowsUnauthorized() {
        String refreshToken = jwtManager.createRefreshToken(1L);

        assertThatThrownBy(() -> jwtManager.resolveUserId("Bearer " + refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }
}
