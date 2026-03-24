package org.triple.backend.auth.jwt;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.global.log.MaskUtil;

@Slf4j
@Component
public class JwtUserArgumentResolver implements HandlerMethodArgumentResolver {
    public static final String LOGIN_USER_ID = "LOGIN_USER_ID";

    private final JwtManager jwtManager;

    public JwtUserArgumentResolver(final JwtManager jwtManager) {
        this.jwtManager = jwtManager;
    }

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
            @Nullable WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }

        Object loginUser = request.getAttribute(LOGIN_USER_ID);
        if (loginUser instanceof Long userId) {
            log.debug("request attribute userId detected = {}", MaskUtil.maskId(userId));
            return userId;
        }

        try {
            Long userId = jwtManager.resolveUserId(request.getHeader(JwtManager.AUTHORIZATION_HEADER));
            if (userId != null) {
                log.debug("authorization header userId detected = {}", MaskUtil.maskId(userId));
            }
            return userId;
        } catch (BusinessException e) {
            return null;
        }
    }
}
