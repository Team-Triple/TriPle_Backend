package org.triple.backend.payment.infra.exception;

public class ConfirmAnonymousException extends RuntimeException {

    public ConfirmAnonymousException(String message) {
        super(message);
    }

    public ConfirmAnonymousException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
