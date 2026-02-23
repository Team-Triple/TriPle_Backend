package org.triple.backend.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.triple.backend.auth.cookie.CookieManager;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.service.AuthService;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.auth.session.LoginRequired;

import static org.triple.backend.global.log.MaskUtil.maskString;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CsrfTokenManager csrfTokenManager;
    private final CookieManager cookieManager;

    @PostMapping("/login")
    public AuthLoginResponseDto login(@Valid @RequestBody final AuthLoginRequestDto authLoginRequestDto,
                                      final HttpServletRequest request,
                                      final HttpServletResponse response) {
        AuthLoginResponseDto result = authService.login(authLoginRequestDto, request);
        String token = csrfTokenManager.getOrCreateToken(request);
        cookieManager.addLoginCookie(response);
        response.setHeader(CsrfTokenManager.CSRF_HEADER, token);
        log.debug("응답 시점 JSESSIONID 쿠키 = {}", maskString((request.getSession(false) == null ? "none" : request.getSession(false).getId())));
        log.debug("응답 시점 헤더의 Csrf 토큰 = {}", maskString(response.getHeader(CsrfTokenManager.CSRF_HEADER)));
        return result;
    }

    @LoginRequired
    @PostMapping("/logout")
    public void logout(
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        authService.logout(request);
        cookieManager.clearLoginCookie(response);
        cookieManager.clearSessionCookie(response);
    }
}
