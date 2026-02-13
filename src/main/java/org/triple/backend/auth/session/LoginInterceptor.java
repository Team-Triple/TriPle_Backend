package org.triple.backend.auth.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private static final String LOGIN_USER_ID = "LOGIN_USER_ID";

    private final SessionManager sessionManger;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) return true;
        boolean required = handlerMethod.hasMethodAnnotation(LoginRequired.class) || handlerMethod.getBeanType().isAnnotationPresent(LoginRequired.class);

        if(!required) return true;

        Long userId = sessionManger.getUserIdOrThrow(request);
        request.setAttribute(LOGIN_USER_ID, userId);

        return true;
    }
}
