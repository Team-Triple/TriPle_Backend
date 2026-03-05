package org.triple.backend.user.exception;

import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자 입니다.");

    private HttpStatus status;
    private String message;

    UserErrorCode(HttpStatus status, String message) {
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
