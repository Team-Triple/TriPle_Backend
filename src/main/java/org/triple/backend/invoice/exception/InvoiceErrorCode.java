package org.triple.backend.invoice.exception;

import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

public enum InvoiceErrorCode implements ErrorCode {

    DUPLICATE_RECIPIENT(HttpStatus.CONFLICT, "청구 대상에 중복된 사용자가 포함되어 있습니다."),
    INVALID_TOTAL_AMOUNT(HttpStatus.FORBIDDEN, "총 금액이 일치하지 않습니다."),
    RECIPIENT_USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "청구 대상자에 존재하지 않는 사용자가 포함되어 있습니다.");

    private HttpStatus status;
    private String message;

    InvoiceErrorCode(final HttpStatus status, final String message) {
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
