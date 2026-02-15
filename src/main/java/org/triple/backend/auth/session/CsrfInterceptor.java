package org.triple.backend.auth.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.global.error.BusinessException;

import java.util.Set;

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

        String token = request.getHeader(CsrfTokenManager.CSRF_HEADER);
        if (!csrfTokenManager.isValid(request, token)) {
            throw new BusinessException(AuthErrorCode.INVALID_CSRF_TOKEN);
        }

        return true;
    }

    private boolean hasLoginRequiredAnnotation(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(LoginRequired.class)
                || handlerMethod.getBeanType().isAnnotationPresent(LoginRequired.class);
    }
}
