package org.triple.backend.travel.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.travel.dto.request.TravelSaveRequestDto;
import org.triple.backend.travel.dto.response.TravelSaveResponseDto;
import org.triple.backend.travel.service.TravelService;

@RestController
@RequestMapping("/travels")
@RequiredArgsConstructor
public class TravelController {
    private final TravelService travelService;

    @LoginRequired
    @PostMapping
    public TravelSaveResponseDto saveTravels(
            @Valid @RequestBody TravelSaveRequestDto travelSaveRequestDto,
            @LoginUser Long userId
    ) {
        return travelService.saveTravels(travelSaveRequestDto, userId);
    }
}
