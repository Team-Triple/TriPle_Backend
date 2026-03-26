package org.triple.backend.auth.jwt;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.triple.backend.auth.config.property.JwtProperties;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class JwtCookieWriter {

    private final JwtProperties jwtProperties;

    public void writeRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie refreshCookie = ResponseCookie.from(jwtProperties.refreshCookieName(), refreshToken)
                .httpOnly(true)
                .secure(jwtProperties.refreshCookieSecure())
                .sameSite(jwtProperties.refreshCookieSameSite())
                .path(jwtProperties.refreshCookiePath())
                .maxAge(Duration.ofSeconds(jwtProperties.refreshTokenExpireSeconds()))
                .build();

        response.addHeader("Set-Cookie", refreshCookie.toString());
    }
}
