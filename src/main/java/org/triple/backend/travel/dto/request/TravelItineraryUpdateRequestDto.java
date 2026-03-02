package org.triple.backend.travel.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record TravelItineraryUpdateRequestDto(
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        @Size(max = 100, message = "여행 설명은 최대 100자까지 입력할 수 있습니다.") String description,
        String thumbnailUrl,
        @Min(value = 1, message = "멤버 수 제한은 1명 이상이어야 합니다.") Integer memberLimit
) {
}
