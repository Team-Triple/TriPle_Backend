package org.triple.backend.payment.exception;

import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

public enum PaymentErrorCode implements ErrorCode {
    NOT_FOUND_PAYMENT(HttpStatus.NOT_FOUND, "해당 결제는 유효하지 않습니다."),
    ALREADY_PROCESSED_PAYMENT(HttpStatus.CONFLICT, "해당 결제는 이미 처리되었습니다."),
    ILLEGAL_AMOUNT(HttpStatus.BAD_REQUEST, "올바르지 않은 결제 가격입니다."),
    CONFIRM_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "알 수 없는 예외로 결제 승인에 실패했습니다."),
    DUPLICATED_PAYMENT(HttpStatus.CONFLICT, "중복된 결제 생성 요청입니다."),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 결제가 완료된 청구 대상입니다."),
    PAYMENT_AMOUNT_EXCEEDS_REMAINING(HttpStatus.CONFLICT, "요청 결제 금액이 남은 금액을 초과합니다."),
    PAYMENT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "결제를 진행할 수 없는 청구서입니다."),
    PAYMENT_CONFIRM_NOT_ALLOWED(HttpStatus.FORBIDDEN, "결제 승인 권한이 없습니다."),
    PAYMENT_SEARCH_NOT_ALLOWED(HttpStatus.FORBIDDEN, "결제 조회 권한이 없습니다."),
    PAYMENT_ALREADY_IS_ACTIVE(HttpStatus.FORBIDDEN, "이미 실행중인 결제입니다."),
    INVALID_SEARCH_KEYWORD_LENGTH(HttpStatus.BAD_REQUEST, "검색어는 최대 20자까지 입력할 수 있습니다.");

    PaymentErrorCode(final HttpStatus status, final String message) {
        this.status = status;
        this.message = message;
    }

    private HttpStatus status;
    private String message;

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
