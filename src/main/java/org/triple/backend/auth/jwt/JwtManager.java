package org.triple.backend.auth.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.triple.backend.auth.config.property.JwtProperties;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.global.error.BusinessException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Base64;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtManager {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID = "userId";
    private static final String TOKEN_TYPE = "tokenType";
    private static final String ACCESS_TYPE = "ACCESS";
    private static final String REFRESH_TYPE = "REFRESH";

    private final JwtProperties jwtProperties;

    public String createAccessToken(Long userId) {
        return createToken(userId, ACCESS_TYPE, jwtProperties.accessTokenExpireSeconds());
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, REFRESH_TYPE, jwtProperties.refreshTokenExpireSeconds());
    }

    private String createToken(Long userId, String tokenType, long expireSeconds) {
        SecretKey secretKey = secretKey();
        Instant now = Instant.now();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim(USER_ID, userId)
                .claim(TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public Long resolveUserId(String jwtHeader) {
        if (jwtHeader == null || jwtHeader.isBlank()) return null;

        if (!jwtHeader.startsWith(BEARER_PREFIX)) throw new BusinessException(AuthErrorCode.UNAUTHORIZED);

        String token = jwtHeader.substring(BEARER_PREFIX.length());

        if (token.isBlank()) throw new BusinessException(AuthErrorCode.UNAUTHORIZED);

        try {
            Claims claims = parse(token);
            validateTokenType(claims, ACCESS_TYPE);
            return extractUserId(claims);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
    }

    public Long resolveUserIdFromRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }

        try {
            Claims claims = parse(refreshToken);
            validateTokenType(claims, REFRESH_TYPE);
            return extractUserId(claims);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
    }

    public String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        return Base64.getEncoder().encodeToString(hash(token));
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Long extractUserId(Claims claims) {
        Object userIdObj = claims.get(USER_ID);
        if (userIdObj instanceof Number number) {
            return number.longValue();
        }
        if (userIdObj instanceof String value && !value.isBlank()) {
            return Long.parseLong(value);
        }
        throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
    }

    private void validateTokenType(Claims claims, String expectedType) {
        Object tokenTypeObj = claims.get(TOKEN_TYPE);
        if (!(tokenTypeObj instanceof String tokenType)) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        if (!expectedType.equals(tokenType)) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(hash(jwtProperties.secret()));
    }

    private byte[] hash(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JWT secret hash initialization failed.", e);
        }
    }
}
