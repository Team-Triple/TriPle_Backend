package org.triple.backend.auth.exception;

public class OauthTransientException extends RuntimeException {

    public OauthTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
