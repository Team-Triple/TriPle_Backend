package org.triple.backend.travel.dto.response;

import org.triple.backend.travel.entity.TravelItinerary;

import java.time.LocalDateTime;
import java.util.List;

public record TravelItineraryCursorResponseDto(
        List<TravelSummaryDto> items,
        Long nextCursor,
        boolean hasNext
) {
    public record TravelSummaryDto(
            String title,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String thumbnailUrl,
            int memberCount
    ) {
    }

    public static TravelItineraryCursorResponseDto of(
            final List<TravelItinerary> travelItineraries,
            final Long nextCursor,
            final boolean hasNext
    ) {
        List<TravelSummaryDto> items = travelItineraries.stream()
                .map(travelItinerary -> new TravelSummaryDto(
                        travelItinerary.getTitle(),
                        travelItinerary.getDescription(),
                        travelItinerary.getStartAt(),
                        travelItinerary.getEndAt(),
                        travelItinerary.getThumbnailUrl(),
                        travelItinerary.getMemberCount()
                ))
                .toList();

        return new TravelItineraryCursorResponseDto(items, nextCursor, hasNext);
    }
}
