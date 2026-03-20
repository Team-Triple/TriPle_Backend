package org.triple.backend.travel.dto.response;

import org.triple.backend.travel.entity.TravelItinerary;

import java.time.LocalDateTime;
import java.util.List;

public record TravelItineraryCursorResponseDto(
        List<TravelSummaryDto> items,
        Long nextCursor,
        boolean hasNext,
        long count
) {
    public record TravelSummaryDto(
            Long id,
            String title,
            String description,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int memberCount
    ) {
    }

    public static TravelItineraryCursorResponseDto of(
            final List<TravelItinerary> travelItineraries,
            final Long nextCursor,
            final boolean hasNext,
            final long count
    ) {
        List<TravelSummaryDto> items = travelItineraries.stream()
                .map(travelItinerary -> new TravelSummaryDto(
                        travelItinerary.getId(),
                        travelItinerary.getTitle(),
                        travelItinerary.getDescription(),
                        travelItinerary.getStartAt(),
                        travelItinerary.getEndAt(),
                        travelItinerary.getMemberCount()
                ))
                .toList();

        return new TravelItineraryCursorResponseDto(items, nextCursor, hasNext, count);
    }

    public static TravelItineraryCursorResponseDto countOnly(final long count) {
        return new TravelItineraryCursorResponseDto(List.of(), null, false, count);
    }
}
