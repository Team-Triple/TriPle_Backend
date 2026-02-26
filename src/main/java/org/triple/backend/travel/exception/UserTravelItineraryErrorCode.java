package org.triple.backend.travel.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

@RequiredArgsConstructor
public enum UserTravelItineraryErrorCode implements ErrorCode {
    USER_TRAVEL_ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "여행 내 해당 유저를 찾을 수 없습니다."),
    UPDATE_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "LEADER만 수정할 수 있습니다."),
    DELETE_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "LEADER만 삭제할 수 있습니다."),
    LEAVE_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "LEADER는 여행에서 탈퇴할 수 없습니다."),
    CONCURRENT_TRAVEL_ITINERARY_LEAVE(HttpStatus.CONFLICT, "여행 탈퇴가 동시에 처리되어 실패했습니다. 다시 시도해주세요.");

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
