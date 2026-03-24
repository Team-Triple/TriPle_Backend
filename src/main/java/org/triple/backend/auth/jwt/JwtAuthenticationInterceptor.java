package org.triple.backend.auth.jwt;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.global.log.MaskUtil;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationInterceptor implements HandlerInterceptor {
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String LOGIN_USER_ID = "LOGIN_USER_ID";

    private final JwtManager jwtManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) return true;
        if (!hasLoginRequiredAnnotation(handlerMethod)) return true;

        Long userId = jwtManager.resolveUserId(request.getHeader(AUTHORIZATION_HEADER));
        if (userId == null) throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        log.debug("토큰 정상, userId 정상적으로 받아옴 = {}", MaskUtil.maskId(userId));

        request.setAttribute(LOGIN_USER_ID, userId);
        return true;
    }

    private boolean hasLoginRequiredAnnotation(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(LoginRequired.class)
                || handlerMethod.getBeanType().isAnnotationPresent(LoginRequired.class);
    }
}
