package org.triple.backend.travel.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

@RequiredArgsConstructor
public enum UserTravelItineraryErrorCode implements ErrorCode {
    USER_TRAVEL_ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "여행 내 해당 유저를 찾을 수 없습니다."),
    ALREADY_JOINED_TRAVEL(HttpStatus.CONFLICT, "이미 참가한 여행입니다."),
    UPDATE_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "LEADER만 수정할 수 있습니다."),
    DELETE_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "LEADER만 삭제할 수 있습니다.");

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
