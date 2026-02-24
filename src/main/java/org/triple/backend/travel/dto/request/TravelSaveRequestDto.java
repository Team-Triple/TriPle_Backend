package org.triple.backend.travel.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record TravelSaveRequestDto(
        @NotBlank(message = "제목은 필수입니다!") String title,
        @NotNull(message = "시작일은 필수입니다!") LocalDateTime startAt,
        @NotNull(message = "마지막일은 필수입니다!") LocalDateTime endAt,
        @NotNull(message = "그룹 아이디는 필수입니다!") Long groupId,
        @Size(max = 100, message = "여행 설명은 100글자 제한!") String description,
        String thumbnailUrl,
        @NotNull @Min(value = 1, message = "멤버 수는 1~20 제한!") Integer memberLimit
)
{}
