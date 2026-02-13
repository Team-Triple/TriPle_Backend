package org.triple.backend.auth.session;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.global.error.BusinessException;

@Component
public class SessionManager {

    public static final String SESSION_KEY = "USER_ID";

    public void login(HttpServletRequest request, Long userId) {
        request.getSession(true).setAttribute(SESSION_KEY, userId);
    }

    public @Nullable Long getUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        return (Long) session.getAttribute(SESSION_KEY);
    }

    public Long getUserIdOrThrow(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        return userId;
    }
}
