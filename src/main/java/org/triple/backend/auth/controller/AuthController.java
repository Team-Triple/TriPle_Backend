package org.triple.backend.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.service.AuthService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthLoginResponseDto login(@Valid @RequestBody final AuthLoginRequestDto authLoginRequestDto, final HttpServletRequest request) {
        return authService.login(authLoginRequestDto, request);
    }
}
