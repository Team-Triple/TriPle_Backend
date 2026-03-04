package org.triple.backend.auth.session;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.global.error.BusinessException;

import java.util.UUID;

import static org.triple.backend.global.log.MaskUtil.maskId;
import static org.triple.backend.global.log.MaskUtil.maskString;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionManager {

    public static final String SESSION_KEY = "USER_ID";
    private final UserIdentityResolver userIdentityResolver;
    private final UuidCrypto uuidCrypto;

    public void login(HttpServletRequest request, UUID publicUuid) {
        String encryptedPublicUuid = uuidCrypto.encrypt(publicUuid);
        request.getSession(true).setAttribute(SESSION_KEY, encryptedPublicUuid);
        log.debug("세션에 저장된 encrypted publicUuid = {}", maskString(encryptedPublicUuid));
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            log.debug("무효화할 세션이 없음");
            return;
        }

        Long userId = userIdentityResolver.resolve(session.getAttribute(SESSION_KEY));
        session.invalidate();
        log.debug("세션 무효화 완료, userId = {}", maskId(userId));
    }

    public @Nullable Long getUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return userIdentityResolver.resolve(session.getAttribute(SESSION_KEY));
    }

    public Long getUserIdOrThrow(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            log.warn("세션에 저장된 userId가 없음");
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    public @Nullable Long resolveUserId(@Nullable Object principal) {
        return userIdentityResolver.resolve(principal);
    }
}
