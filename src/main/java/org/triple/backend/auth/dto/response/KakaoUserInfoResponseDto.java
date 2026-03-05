package org.triple.backend.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Properties;

public record KakaoUserInfoResponseDto(
        String id,

        @JsonProperty("properties")
        Properties properties,

        @JsonProperty("kakao_account")
        KakaoAccount kakaoAccount
) {

    public record KakaoAccount(
            KakaoProfile profile,
            String email
    ) {
    }

    public record KakaoProfile(
            String nickname,

            @JsonProperty("profile_image_url")
            String profileImageUrl
    ) {
    }
}