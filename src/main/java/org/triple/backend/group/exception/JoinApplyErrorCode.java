package org.triple.backend.group.exception;

import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

public enum JoinApplyErrorCode implements ErrorCode {

    ALREADY_APPLY_JOIN_REQUEST(HttpStatus.CONFLICT,"이미 가입이 요청된 그룹입니다."),
    ALREADY_JOINED_GROUP(HttpStatus.CONFLICT, "이미 가입된 그룹입니다."),
    REAPPLY_ALLOWED_ONLY_CANCELED(HttpStatus.CONFLICT, "취소된 신청만 재신청할 수 있습니다.");

    private HttpStatus status;
    private String message;

    JoinApplyErrorCode(final HttpStatus status, final String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
