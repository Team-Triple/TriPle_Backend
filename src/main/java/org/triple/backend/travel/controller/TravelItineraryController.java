package org.triple.backend.travel.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.jwt.LoginRequired;
import org.triple.backend.auth.jwt.LoginUser;
import org.triple.backend.travel.dto.request.TravelItinerarySaveRequestDto;
import org.triple.backend.travel.dto.request.TravelItineraryUpdateRequestDto;
import org.triple.backend.travel.dto.response.TravelDocInitialStateResponseDto;
import org.triple.backend.travel.dto.response.TravelItineraryCursorResponseDto;
import org.triple.backend.travel.dto.response.TravelItineraryInfoResponseDto;
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
    @PostMapping("/{travelId}/users/me")
    public void joinTravel(
            @PathVariable Long travelId,
            @LoginUser Long userId
    ) {
        travelItineraryService.joinTravel(travelId, userId);
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

    @GetMapping("/{groupId}")
    public TravelItineraryCursorResponseDto browseTravels(
            @PathVariable Long groupId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size,
            @LoginUser Long userId
    ) {
        return travelItineraryService.browseTravels(groupId, cursor, size, userId);
    }

    @LoginRequired
    @DeleteMapping("/{travelId}/users/me")
    public void leaveTravel(
            @PathVariable Long travelId,
            @LoginUser Long userId
    ) {
        travelItineraryService.leaveTravel(travelId, userId);
    }

    @LoginRequired
    @GetMapping("/{travelId}/info")
    public TravelItineraryInfoResponseDto getTravelInfo(
            @PathVariable Long travelId,
            @LoginUser Long userId
    ) {
        return travelItineraryService.getTravelInfo(travelId, userId);
    }

    @LoginRequired
    @GetMapping("/{travelId}/doc")
    public TravelDocInitialStateResponseDto getTravelDocInitialState(
            @PathVariable Long travelId,
            @LoginUser Long userId
    ) {
        return travelItineraryService.getTravelDocInitialState(travelId, userId);
    }
}
