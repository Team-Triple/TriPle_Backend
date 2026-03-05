package org.triple.backend.auth.cookie;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.triple.backend.auth.config.property.CookieProperties;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class CookieManager {

    public static final String LOGIN_STATUS = "login_status";
    public static final String JSESSION_ID = "JSESSIONID";

    private final CookieProperties cookieProperties;

    public void addLoginCookie(final HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(LOGIN_STATUS, "true")
                .path(cookieProperties.path())
                .httpOnly(false)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .maxAge(Duration.ofDays(1))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearLoginCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(LOGIN_STATUS, "")
                .path(cookieProperties.path())
                .httpOnly(false)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(JSESSION_ID, "")
                .path(cookieProperties.path())
                .httpOnly(cookieProperties.httpOnly())
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
