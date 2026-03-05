package org.triple.backend.file.infra.exception;

import org.springframework.http.HttpStatus;

public class InvalidKeyException extends FinalizeUploadException {
    public InvalidKeyException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public InvalidKeyException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, message, cause);
    }

    public InvalidKeyException(HttpStatus httpStatus, String message) {
        super(httpStatus, message);
    }

    public InvalidKeyException(HttpStatus httpStatus, String message, Throwable cause) {
        super(httpStatus, message, cause);
    }
}
