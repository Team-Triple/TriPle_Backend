package org.triple.backend.travel.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.travel.dto.request.TravelItinerarySaveRequestDto;
import org.triple.backend.travel.dto.request.TravelItineraryUpdateRequestDto;
import org.triple.backend.travel.dto.response.TravelItinerarySaveResponseDto;
import org.triple.backend.travel.service.TravelItineraryService;

@RestController
@RequestMapping("/travels")
@RequiredArgsConstructor
public class TravelItineraryController {
    private final TravelItineraryService travelItineraryService;

    @LoginRequired
    @PostMapping
    public TravelItinerarySaveResponseDto saveTravelItinerary(
            @Valid @RequestBody TravelItinerarySaveRequestDto travelItinerarySaveRequestDto,
            @LoginUser Long userId
    ) {
        return travelItineraryService.saveTravels(travelItinerarySaveRequestDto, userId);
    }

    @LoginRequired
    @PatchMapping("/{travelId}")
    public void updateTravel(
            @Valid @RequestBody TravelItineraryUpdateRequestDto travelItineraryUpdateRequestDto,
            @PathVariable Long travelId,
            @LoginUser Long userId
    ) {
        travelItineraryService.updateTravel(travelItineraryUpdateRequestDto, travelId, userId);
    }

    @LoginRequired
    @DeleteMapping("/{travelId}")
    public void deleteTravel(
            @PathVariable Long travelId,
            @LoginUser Long userId
    ) {
        travelItineraryService.deleteTravel(travelId, userId);
    }

}
