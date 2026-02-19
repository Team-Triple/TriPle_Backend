package org.triple.backend.auth.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.global.error.BusinessException;

import java.util.Set;

import static org.triple.backend.global.log.MaskUtil.maskString;

@Slf4j
@Component
@RequiredArgsConstructor
public class CsrfInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final CsrfTokenManager csrfTokenManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) return true;
        if (SAFE_METHODS.contains(request.getMethod())) return true;
        if (!hasLoginRequiredAnnotation(handlerMethod)) return true;
        log.debug("로그인이 필요한 요청 Csrf 토큰 검사 시작");

        String token = request.getHeader(CsrfTokenManager.CSRF_HEADER);
        log.debug("헤더에서 Csrf 토큰 받아옴 = {}", maskString(token));
        if (!csrfTokenManager.isValid(request, token)) {
            log.warn("요청 헤더, 세션 각각의 Csrf 토큰이 같지 않음");
            throw new BusinessException(AuthErrorCode.INVALID_CSRF_TOKEN);
        }

        return true;
    }

    private boolean hasLoginRequiredAnnotation(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(LoginRequired.class)
                || handlerMethod.getBeanType().isAnnotationPresent(LoginRequired.class);
    }
}
