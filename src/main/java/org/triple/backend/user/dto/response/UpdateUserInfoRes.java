package org.triple.backend.user.dto.response;

import java.time.LocalDate;
import lombok.Builder;

@Builder
public record UpdateUserInfoRes(
    String userId,
    String nickname,
    String gender,
    LocalDate birth,
    String description,
    String profileUrl
) {}