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
            new JwtProperties("test-jwt-secret-value-at-least-32-characters", 3600)
    );

    @Test
    @DisplayName("JWT 생성 후 Authorization 헤더에서 userId를 복원한다")
    void create_and_parse_success() {
        String token = jwtManager.createAccessToken(1L);

        Long userId = jwtManager.resolveUserId("Bearer " + token);

        assertThat(userId).isEqualTo(1L);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 null을 반환한다")
    void missing_header_returns_null() {
        Long userId = jwtManager.resolveUserId(null);

        assertThat(userId).isNull();
    }

    @Test
    @DisplayName("잘못된 토큰이면 UNAUTHORIZED 예외를 던진다")
    void invalid_token_throws_unauthorized() {
        assertThatThrownBy(() -> jwtManager.resolveUserId("Bearer invalid-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }
}
