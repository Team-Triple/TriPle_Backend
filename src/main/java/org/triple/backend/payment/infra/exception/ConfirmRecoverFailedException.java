package org.triple.backend.payment.infra.exception;

public class ConfirmRecoverFailedException extends RuntimeException {

    public ConfirmRecoverFailedException(String message) {
        super(message);
    }

    public ConfirmRecoverFailedException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
