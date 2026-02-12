package org.triple.backend.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoTokenResponseDto(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        Integer expiresIn,

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("refresh_token_expires_in")
        Integer refreshTokenExpiresIn,

        String scope
) {
}
