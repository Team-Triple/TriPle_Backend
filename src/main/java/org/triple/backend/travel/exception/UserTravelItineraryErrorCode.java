package org.triple.backend.travel.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

@RequiredArgsConstructor
public enum UserTravelItineraryErrorCode implements ErrorCode {
    USER_TRAVEL_ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "\ud574\ub2f9 \uc720\uc800\ub294 \uc5ec\ud589\uc77c\uc815\uc5d0 \uc5c6\uc2b5\ub2c8\ub2e4."),
    UPDATE_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "LEADER\ub9cc \uc5ec\ud589 \uc77c\uc815\uc744 \ubcc0\uacbd\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4."),
    DELETE_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "LEADER\ub9cc \uc5ec\ud589 \uc77c\uc815\uc744 \uc0ad\uc81c\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4.");

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
