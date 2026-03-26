package org.triple.backend.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.oauth.OauthUser;

@Service
@RequiredArgsConstructor
public class AuthServiceFacade {
    private final AuthService authService;

    public AuthLoginResponseDto login(final AuthLoginRequestDto authLoginRequestDto, HttpServletResponse response) {
        OauthUser oauthUser = authService.authenticate(authLoginRequestDto);
        return authService.findOrCreate(oauthUser, response);
    }

    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        authService.reissueAccessToken(request, response);
    }
}
