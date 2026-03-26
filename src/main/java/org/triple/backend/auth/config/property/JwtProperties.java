package org.triple.backend.auth.config.property;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Min(1) long accessTokenExpireSeconds,
        @Min(1) long refreshTokenExpireSeconds,
        @NotBlank String refreshCookieName,
        @NotBlank String refreshCookiePath,
        boolean refreshCookieSecure,
        @NotBlank String refreshCookieSameSite
) {
}
