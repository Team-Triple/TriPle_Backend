package org.triple.backend.auth.oauth.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoOauthProperties(
        String tokenUri,
        String userInfoUri,
        String clientId,
        String clientSecret,
        String redirectUri
) {
}
