package org.triple.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.global.error.BusinessException;

@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String USER_ID = "USER_ID";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && Long.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public @Nullable Long resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

        HttpSession session = request.getSession(false);

        if (session == null) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }

        Object userId = session.getAttribute(USER_ID);
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }

        return (Long) userId;
    }
}
