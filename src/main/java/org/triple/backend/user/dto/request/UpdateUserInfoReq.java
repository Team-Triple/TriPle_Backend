package org.triple.backend.user.dto.request;

import java.time.LocalDate;
import org.triple.backend.user.entity.Gender;

public record UpdateUserInfoReq(
    String nickname,
    Gender gender,
    LocalDate birth,
    String description,
    String profileUrl
) {}
