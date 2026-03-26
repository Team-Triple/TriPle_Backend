package org.triple.backend.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.auth.config.property.JwtProperties;
import org.triple.backend.auth.crypto.UuidToUserIdCache;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.entity.RefreshToken;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.jwt.JwtCookieWriter;
import org.triple.backend.auth.jwt.JwtManager;
import org.triple.backend.auth.oauth.OauthClient;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.oauth.OauthUser;
import org.triple.backend.auth.repository.RefreshTokenJpaRepository;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.global.log.MaskUtil;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String BEARER_PREFIX = "Bearer ";

    private final UuidToUserIdCache uuidToUserIdCache;
    private final Map<OauthProvider, OauthClient> clients;
    private final UserJpaRepository userJpaRepository;
    private final RefreshTokenJpaRepository refreshTokenJpaRepository;
    private final JwtManager jwtManager;
    private final JwtCookieWriter jwtCookieWriter;
    private final JwtProperties jwtProperties;

    public OauthUser authenticate(AuthLoginRequestDto authLoginRequestDto) {
        OauthClient client = clients.get(authLoginRequestDto.provider());
        if (client == null) {
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
        }
        log.debug("client = {}", client.provider());
        return client.fetchUser(authLoginRequestDto.code());
    }

    @Transactional
    public AuthLoginResponseDto findOrCreate(OauthUser oauthUser, HttpServletResponse response) {
        User user = userJpaRepository.findByProviderAndProviderId(oauthUser.provider(), oauthUser.providerId())
                .orElseGet(() -> signup(oauthUser));

        user.assignPublicUuidIfAbsent();
        uuidToUserIdCache.save(user.getPublicUuid(), user.getId());
        log.debug("user session initialized: {}", MaskUtil.maskString(user.getPublicUuid().toString()));

        String accessToken = jwtManager.createAccessToken(user.getId());
        String refreshToken = jwtManager.createRefreshToken(user.getId());
        replaceRefreshToken(user.getId(), refreshToken);

        response.setHeader(JwtManager.AUTHORIZATION_HEADER, BEARER_PREFIX + accessToken);
        jwtCookieWriter.writeRefreshCookie(response, refreshToken);
        return AuthLoginResponseDto.from(user);
    }

    @Transactional
    public void reissueAccessToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = resolveRefreshTokenFromCookie(request);
        Long userId = jwtManager.resolveUserIdFromRefreshToken(refreshToken);

        RefreshToken storedRefreshToken = refreshTokenJpaRepository.findTopByUserIdOrderByIdDesc(userId)
                .orElseThrow(this::unauthorized);
        validateStoredRefreshToken(storedRefreshToken, refreshToken);

        String nextAccessToken = jwtManager.createAccessToken(userId);
        String nextRefreshToken = jwtManager.createRefreshToken(userId);
        replaceRefreshToken(userId, nextRefreshToken);

        response.setHeader(JwtManager.AUTHORIZATION_HEADER, BEARER_PREFIX + nextAccessToken);
        jwtCookieWriter.writeRefreshCookie(response, nextRefreshToken);
    }

    private String resolveRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw unauthorized();
        }

        for (Cookie cookie : cookies) {
            if (!jwtProperties.refreshCookieName().equals(cookie.getName())) {
                continue;
            }

            String refreshToken = cookie.getValue();
            if (refreshToken == null || refreshToken.isBlank()) {
                throw unauthorized();
            }
            return refreshToken;
        }

        throw unauthorized();
    }

    private void validateStoredRefreshToken(RefreshToken storedRefreshToken, String refreshToken) {
        String tokenHash = storedRefreshToken.getTokenHash();
        if (tokenHash == null || !tokenHash.equals(jwtManager.hashToken(refreshToken))) {
            throw unauthorized();
        }

        LocalDateTime expiresAt = storedRefreshToken.getExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
            throw unauthorized();
        }
    }

    private void replaceRefreshToken(Long userId, String refreshToken) {
        String tokenHash = jwtManager.hashToken(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpireSeconds());

        refreshTokenJpaRepository.deleteByUserId(userId);
        RefreshToken nextRefreshToken = RefreshToken.create(userId, tokenHash, expiresAt);
        refreshTokenJpaRepository.save(nextRefreshToken);
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

    private BusinessException unauthorized() {
        return new BusinessException(AuthErrorCode.UNAUTHORIZED);
    }
}
