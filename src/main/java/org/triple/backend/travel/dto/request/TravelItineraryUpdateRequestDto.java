package org.triple.backend.travel.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record TravelItineraryUpdateRequestDto(
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        @Size(max = 100, message = "여행 설명은 100글자 제한!") String description,
        String thumbnailUrl,
        @Min(value = 1, message = "멤버 수는 1~20 제한!") Integer memberLimit
) {
}
