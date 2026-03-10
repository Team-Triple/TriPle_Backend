package org.triple.backend.travel.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record TravelItinerarySaveRequestDto(
        @NotBlank(message = "제목은 필수입니다.") String title,
        @NotNull(message = "시작일은 필수입니다.") LocalDateTime startAt,
        @NotNull(message = "종료일은 필수입니다.") LocalDateTime endAt,
        @NotNull(message = "그룹 ID는 필수입니다.") Long groupId,
        @Size(max = 100, message = "여행 설명은 최대 100자까지 입력할 수 있습니다.") String description,
        String thumbnailUrl
)
{}
