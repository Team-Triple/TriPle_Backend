package org.triple.backend.travel.dto.response;

import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;

import java.time.LocalDateTime;
import java.util.List;

public record TravelItineraryInfoResponseDto(
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        List<TravelMemberDto> members
) {
    public record TravelMemberDto(
            String nickname,
            String profileUrl,
            UserRole userRole
    ) {
    }

    public static TravelItineraryInfoResponseDto of(
            final TravelItinerary travelItinerary,
            final List<UserTravelItinerary> userTravelItineraries
    ) {
        List<TravelMemberDto> members = userTravelItineraries.stream()
                .map(ut -> new TravelMemberDto(
                        ut.getUser().getNickname(),
                        ut.getUser().getProfileUrl(),
                        ut.getUserRole()
                ))
                .toList();

        return new TravelItineraryInfoResponseDto(
                travelItinerary.getTitle(),
                travelItinerary.getStartAt(),
                travelItinerary.getEndAt(),
                members
        );
    }
}
