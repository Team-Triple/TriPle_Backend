package org.triple.backend.auth.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
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

@Component
@RequiredArgsConstructor
public class JwtManager {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID = "userId";

    private final JwtProperties jwtProperties;

    public String createAccessToken(Long userId) {
        SecretKey secretKey = secretKey();
        Instant now = Instant.now();

        return Jwts.builder()
                .claim(USER_ID, userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwtProperties.accessTokenExpireSeconds())))
                .signWith(secretKey)
                .compact();
    }

    public Long resolveUserId(String jwtHeader) {
        if (jwtHeader == null || jwtHeader.isBlank()) return null;

        if (!jwtHeader.startsWith(BEARER_PREFIX)) throw new BusinessException(AuthErrorCode.UNAUTHORIZED);

        String token = jwtHeader.substring(BEARER_PREFIX.length());

        if (token.isBlank()) throw new BusinessException(AuthErrorCode.UNAUTHORIZED);

        try {
            Object userIdObj = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get(USER_ID);

            if (userIdObj instanceof Number number) {
                return number.longValue();
            }
            if (userIdObj instanceof String value && !value.isBlank()) {
                return Long.parseLong(value);
            }
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        } catch (JwtException | IllegalArgumentException e) {
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
