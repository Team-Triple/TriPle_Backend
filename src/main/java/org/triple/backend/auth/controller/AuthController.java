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
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.service.AuthServiceFacade;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthServiceFacade authServiceFacade;

    @PostMapping("/login")
    public AuthLoginResponseDto login(@Valid @RequestBody final AuthLoginRequestDto authLoginRequestDto,
                                      HttpServletResponse response) {
        log.debug("login start");
        return authServiceFacade.login(authLoginRequestDto, response);
    }

    @PostMapping("/refresh")
    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        log.debug("refresh start");
        authServiceFacade.refresh(request, response);
    }
}
