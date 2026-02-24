package org.triple.backend.travel.dto.response;


public record TravelItinerarySaveResponseDto(
        Long itineraryId
) {
    public static TravelItinerarySaveResponseDto from(Long itineraryId) {
        return new TravelItinerarySaveResponseDto(itineraryId);
    }
}
