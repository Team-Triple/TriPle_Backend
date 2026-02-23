package org.triple.backend.auth.session;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.global.error.BusinessException;

import static org.triple.backend.global.log.MaskUtil.maskId;

@Slf4j
@Component
public class SessionManager {

    public static final String SESSION_KEY = "USER_ID";

    public void login(HttpServletRequest request, Long userId) {
        request.getSession(true).setAttribute(SESSION_KEY, userId);
        log.debug("세션에 저장된 userId = {}", maskId(userId));
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            log.debug("무효화할 세션이 없음");
            return;
        }

        Long userId = (Long) session.getAttribute(SESSION_KEY);
        session.invalidate();
        log.debug("세션 무효화 완료, userId = {}", maskId(userId));
    }

    public @Nullable Long getUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        return (Long) session.getAttribute(SESSION_KEY);
    }

    public Long getUserIdOrThrow(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            log.warn("세션에 저장된 userId가 없음");
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
