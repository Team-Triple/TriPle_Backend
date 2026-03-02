package org.triple.backend.payment.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {
    NOT_FOUND_PAYMENT(HttpStatus.NOT_FOUND, "해당 결제는 유효하지 않습니다."),
    ALREADY_PROCESSED_PAYMENT(HttpStatus.CONFLICT, "해당 결제는 이미 처리되었습니다."),
    ILLEGAL_AMOUNT(HttpStatus.BAD_REQUEST, "올바르지 않은 결제 가격입니다."),
    CONFIRM_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "알 수 없는 예외로 결제 승인에 실패했습니다.");

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
