package org.triple.backend.user.dto.response;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record UserInfoResponseDto(
        String publicUuid,
        String nickname,
        String gender,
        LocalDate birth,
        String description,
        String profileUrl
) {
}
