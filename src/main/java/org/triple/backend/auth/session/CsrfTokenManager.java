package org.triple.backend.auth.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class CsrfTokenManager {

    public static final String CSRF_TOKEN_KEY = "CSRF_TOKEN";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    public String getOrCreateToken(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute(CSRF_TOKEN_KEY);
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute(CSRF_TOKEN_KEY, token);
        }
        return token;
    }

    public boolean isValid(HttpServletRequest request, String providedToken) {
        if (providedToken == null || providedToken.isBlank()) {
            log.warn("요청에 Csrf 토큰이 없음");
            return false;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            log.warn("세션에 Csrf 토큰이 없음");
            return false;
        }
        String expected = (String) session.getAttribute(CSRF_TOKEN_KEY);
        return providedToken.equals(expected);
    }
}
