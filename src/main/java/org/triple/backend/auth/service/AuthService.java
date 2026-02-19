package org.triple.backend.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import static org.triple.backend.global.log.MaskUtil.maskId;

@Slf4j
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
        log.debug("로그인 시 매핑된 OauthClient = {}", client.provider());

        OauthUser oauthUser = client.fetchUser(authLoginRequestDto.code());
        log.debug("코드를 통해 {}로부터 유저를 잘 받아왔는가? {}", client.provider(), oauthUser != null);
        User user = findOrCreateUser(oauthUser);
        log.debug("생성된 유저 ID = {}", maskId(user.getId()));

        sessionManager.login(request, user.getId());

        return new AuthLoginResponseDto(
                user.getNickname(),
                user.getEmail(),
                user.getProfileUrl()
        );
    }

    public void logout(final HttpServletRequest request) {
        sessionManager.logout(request);
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
