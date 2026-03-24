package org.triple.backend.auth.service;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.auth.crypto.UuidToUserIdCache;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.jwt.JwtManager;
import org.triple.backend.auth.oauth.OauthClient;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.oauth.OauthUser;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.global.log.MaskUtil;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UuidToUserIdCache uuidToUserIdCache;
    private final Map<OauthProvider, OauthClient> clients;
    private final UserJpaRepository userJpaRepository;
    private final JwtManager jwtManager;

    public OauthUser authenticate(AuthLoginRequestDto authLoginRequestDto) {
        OauthClient client = clients.get(authLoginRequestDto.provider());
        if(client == null) {
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
        }
        log.debug("client = {}", client.provider());
        return client.fetchUser(authLoginRequestDto.code());
    }

    @Transactional()
    public AuthLoginResponseDto findOrCreate(OauthUser oauthUser, HttpServletResponse response) {
        User user = userJpaRepository.findByProviderAndProviderId(oauthUser.provider(), oauthUser.providerId())
                .orElseGet(() -> signup(oauthUser));

        user.assignPublicUuidIfAbsent();
        log.debug("유저 정보 정상적으로 받아옴 = {}", MaskUtil.maskString(user.getPublicUuid().toString()));
        uuidToUserIdCache.save(user.getPublicUuid(), user.getId());
        log.debug("유저 정보 정상적으로 캐싱");
        response.setHeader("Authorization", "Bearer " + jwtManager.createAccessToken(user.getId()));
        log.debug("헤더 토큰 = {}", response.getHeader("Authorization"));
        return AuthLoginResponseDto.from(user);
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
