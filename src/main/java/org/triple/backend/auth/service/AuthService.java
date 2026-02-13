package org.triple.backend.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.oauth.OauthClient;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.oauth.OauthUser;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final Map<OauthProvider, OauthClient> clients;
    private final UserJpaRepository userJpaRepository;
    private final TransactionTemplate txTemplate;
    private final SessionManager sessionManager;

    public AuthLoginResponseDto login(final AuthLoginRequestDto authLoginRequestDto, final HttpServletRequest request) {
        OauthClient client = clients.get(authLoginRequestDto.provider());
        if(client == null) {
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
        }

        OauthUser oauthUser = client.fetchUser(authLoginRequestDto.code());
        User user = findOrCreateUser(oauthUser);

        sessionManager.login(request, user.getId());

        return new AuthLoginResponseDto(
                user.getNickname(),
                user.getEmail(),
                user.getProfileUrl()
        );
    }

    private User findOrCreateUser(final OauthUser oauthUser) {
        return txTemplate.execute(
                status -> userJpaRepository.findByProviderAndProviderId(oauthUser.provider(), oauthUser.providerId())
                .orElseGet(() -> signup(oauthUser)));
    }

    private User signup(final OauthUser oauthUser) {
        User user = User.builder()
                .provider(oauthUser.provider())
                .providerId(oauthUser.providerId())
                .nickname(oauthUser.nickname())
                .email(oauthUser.email())
                .profileUrl(oauthUser.profileUrl())
                .build();

        return userJpaRepository.save(user);
    }
}