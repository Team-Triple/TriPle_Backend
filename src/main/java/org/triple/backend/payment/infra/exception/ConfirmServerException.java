package org.triple.backend.payment.infra.exception;

public class ConfirmServerException extends RuntimeException {

    public ConfirmServerException(String message) {
        super(message);
    }

    public ConfirmServerException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
