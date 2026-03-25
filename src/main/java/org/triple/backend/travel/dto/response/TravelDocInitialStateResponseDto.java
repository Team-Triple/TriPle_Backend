package org.triple.backend.travel.dto.response;

import java.util.Base64;

public record TravelDocInitialStateResponseDto(
        Long travelItineraryId,
        String state,
        boolean initialized
) {

    public static TravelDocInitialStateResponseDto initialized(
            final Long travelItineraryId,
            final byte[] state
    ) {
        String encodedState = Base64.getEncoder().encodeToString(state);
        return new TravelDocInitialStateResponseDto(travelItineraryId, encodedState, true);
    }

    public static TravelDocInitialStateResponseDto empty(final Long travelItineraryId) {
        return new TravelDocInitialStateResponseDto(travelItineraryId, "", false);
    }
}
