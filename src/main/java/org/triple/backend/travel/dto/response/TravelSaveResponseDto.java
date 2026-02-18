package org.triple.backend.travel.dto.response;

import lombok.Builder;


public record TravelSaveResponseDto(
        Long itineraryId
) {
    public static TravelSaveResponseDto from(Long itineraryId) {
        return new TravelSaveResponseDto(itineraryId);
    }
}
