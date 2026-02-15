package org.triple.backend.auth.cookie;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieManager {

    public static final String LOGIN_STATUS = "login_status";

    public void addLoginCookie(final HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(LOGIN_STATUS, "true")
                .path("/")
                .httpOnly(false)
                .secure(true)
                .sameSite("None")
                .maxAge(Duration.ofDays(1))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearLoginCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(LOGIN_STATUS, "")
                .path("/")
                .httpOnly(false)
                .secure(true)
                .sameSite("None")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}