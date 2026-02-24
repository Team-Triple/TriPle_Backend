package org.triple.backend.travel.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

@RequiredArgsConstructor
public enum UserTravelItineraryErrorCode implements ErrorCode {
    USER_TRAVEL_ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 유저는 여행일정에 없습니다."),
    UPDATE_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "LEADER만 여행 일정을 변경할 수 있습니다.")
    ;

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public HttpStatus getStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}