package org.triple.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.triple.backend.auth.oauth.OauthProvider;

public record AuthLoginRequestDto(
        @NotBlank
        String code,

        @NotNull
        OauthProvider provider
) {
}
