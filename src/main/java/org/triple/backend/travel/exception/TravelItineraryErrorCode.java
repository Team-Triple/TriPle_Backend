package org.triple.backend.travel.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

@RequiredArgsConstructor
public enum TravelItineraryErrorCode implements ErrorCode {
    SAVE_FORBIDDEN(HttpStatus.FORBIDDEN, "\ud574\ub2f9 \uadf8\ub8f9 \uc0ac\uc6a9\uc790\ub9cc \uc5ec\ud589\uc744 \uc0dd\uc131\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4."),
    TRAVEL_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "\ud574\ub2f9 \uc720\uc800 \uc815\ubcf4\uac00 \uc5c6\uc2b5\ub2c8\ub2e4."),
    TRAVEL_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "\ud574\ub2f9 \uadf8\ub8f9 \uc815\ubcf4\uac00 \uc5c6\uc2b5\ub2c8\ub2e4."),
    TRAVEL_NOT_FOUND(HttpStatus.NOT_FOUND, "\ud574\ub2f9 \uc5ec\ud589\uc744 \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."),
    CONCURRENT_TRAVEL_ITINERARY_UPDATE(HttpStatus.CONFLICT, "\ub3d9\uc2dc\uc5d0 \uc5ec\ud589 \uc77c\uc815 \uba54\uc778\uc774 \uc218\uc815\ub418\uc5c8\uc2b5\ub2c8\ub2e4. \ub2e4\uc2dc \uc2dc\ub3c4\ud574\uc8fc\uc138\uc694."),
    CONCURRENT_TRAVEL_ITINERARY_DELETE(HttpStatus.CONFLICT, "\ub3d9\uc2dc\uc5d0 \uc5ec\ud589 \uc77c\uc815\uc774 \uc0ad\uc81c\ub418\uc5c8\uc2b5\ub2c8\ub2e4. \ub2e4\uc2dc \uc2dc\ub3c4\ud574\uc8fc\uc138\uc694.");

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
