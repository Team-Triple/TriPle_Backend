package org.triple.backend.invoice.exception;

import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

public enum InvoiceErrorCode implements ErrorCode {

    NOT_FOUND_INVOICE(HttpStatus.NOT_FOUND, "존재하지 않는 청구서 입니다."),
    DUPLICATE_RECIPIENT(HttpStatus.CONFLICT, "청구 대상에 중복된 사용자가 포함되어 있습니다."),
    INVALID_TOTAL_AMOUNT(HttpStatus.FORBIDDEN, "총 금액과 대상 금액 합계가 일치하지 않습니다."),
    RECIPIENT_USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "청구 대상자에 존재하지 않는 사용자가 포함되어 있습니다."),
    DUPLICATE_INVOICE(HttpStatus.CONFLICT, "중복된 청구서 생성입니다."),
    NOT_TRAVEL_LEADER(HttpStatus.FORBIDDEN, "여행장만 청구서를 생성할 수 있습니다."),
    USER_TRAVEL_ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 여행의 참여 멤버가 아닙니다."),
    INVOICE_UPDATE_NOT_ALLOWED_STATUS(HttpStatus.CONFLICT, "청구서를 수정할 수 없습니다."),
    UPDATE_FORBIDDEN_PAYMENT_EXISTS(HttpStatus.CONFLICT, "결제 내역이 있는 청구서는 수정할 수 없습니다."),
    CONCURRENT_INVOICE_UPDATE(HttpStatus.CONFLICT, "동시에 청구서가 수정되었습니다. 다시 시도해주세요."),
    DELETE_UNAUTHORIZED(HttpStatus.FORBIDDEN, "청구서 생성자만 삭제할 수 있습니다."),
    DELETE_FORBIDDEN_STATUS(HttpStatus.CONFLICT, "현재 상태에서는 청구서를 삭제할 수 없습니다."),
    DELETE_FORBIDDEN_PAYMENT_EXISTS(HttpStatus.CONFLICT, "결제 내역이 있는 청구서는 삭제할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

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
