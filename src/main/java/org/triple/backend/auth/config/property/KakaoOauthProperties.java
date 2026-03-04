package org.triple.backend.auth.config.property;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoOauthProperties(
        @NotBlank String tokenUri,
        @NotBlank String userInfoUri,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String redirectUri
) {
}
