package org.triple.backend.auth.dto.response;

public record AuthLoginResponseDto(
        String nickname,
        String email,
        String profileUrl
) {
}
