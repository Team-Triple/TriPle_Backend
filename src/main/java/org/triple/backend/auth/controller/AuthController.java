package org.triple.backend.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.service.AuthService;
import org.triple.backend.auth.session.CsrfTokenManager;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CsrfTokenManager csrfTokenManager;

    @PostMapping("/login")
    public AuthLoginResponseDto login(@Valid @RequestBody final AuthLoginRequestDto authLoginRequestDto,
                                      final HttpServletRequest request,
                                      final HttpServletResponse response) {
        AuthLoginResponseDto result = authService.login(authLoginRequestDto, request);
        String token = csrfTokenManager.getOrCreateToken(request);
        response.setHeader(CsrfTokenManager.CSRF_HEADER, token);
        return result;
    }
}
