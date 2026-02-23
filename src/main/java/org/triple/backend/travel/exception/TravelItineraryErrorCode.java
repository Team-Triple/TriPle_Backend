package org.triple.backend.travel.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

@RequiredArgsConstructor
public enum TravelItineraryErrorCode implements ErrorCode {
    SAVE_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 그룹 사용자만 여행을 생성할 수 있습니다."),
    TRAVEL_USER_NOT_FOUND(HttpStatus.NOT_FOUND,"해당 유저 정보가 없습니다."),
    TRAVEL_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 그룹 정보가 없습니다."),
    TRAVEL_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 여행을 찾을 수 없습니다."),
    CONCURRENT_TRAVEL_ITINERARY_UPDATE(HttpStatus.CONFLICT, "동시에 여행 일정 메인이 수정되었습니다. 다시 시도해주세요.");

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
