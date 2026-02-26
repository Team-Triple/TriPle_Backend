package org.triple.backend.travel.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

@RequiredArgsConstructor
public enum TravelItineraryErrorCode implements ErrorCode {
    SAVE_FORBIDDEN(HttpStatus.FORBIDDEN, "그룹 내 그룹원만 여행을 생성할 수 있습니다."),
    JOIN_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 그룹 멤버만 여행에 참가할 수 있습니다."),
    TRAVEL_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 여행이 존재하지 않습니다."),
    TRAVEL_MEMBER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "여행 정원이 가득 찼습니다."),
    CONCURRENT_TRAVEL_ITINERARY_UPDATE(HttpStatus.CONFLICT, "여행 동시 수정은 불가능합니다. 다시 시도해주세요."),
    CONCURRENT_TRAVEL_ITINERARY_DELETE(HttpStatus.CONFLICT, "여행이 이미 삭제되었습니다."),
    CONCURRENT_TRAVEL_ITINERARY_JOIN(HttpStatus.CONFLICT, "여행 참가 처리 중 동시성 충돌이 발생했습니다. 다시 시도해주세요.");

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
