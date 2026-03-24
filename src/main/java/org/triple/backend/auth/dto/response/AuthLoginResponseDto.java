package org.triple.backend.auth.dto.response;

import org.triple.backend.user.entity.User;

public record AuthLoginResponseDto(
        String nickname,
        String email,
        String profileUrl
) {
    public static AuthLoginResponseDto from(User user) {
        return new AuthLoginResponseDto(
                user.getNickname(), user.getEmail(), user.getProfileUrl()
        );
    }
}
