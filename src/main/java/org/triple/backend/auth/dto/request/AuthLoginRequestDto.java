package org.triple.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.triple.backend.auth.oauth.OauthProvider;

public record AuthLoginRequestDto(
        @NotBlank(message = "인가 코드는 필수입니다.")
        String code,

        @NotNull(message = "OAuth Provider는 필수입니다.")
        OauthProvider provider
) {
}
