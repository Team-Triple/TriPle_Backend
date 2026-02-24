package org.triple.backend.file.infra.exception;

import org.springframework.http.HttpStatus;

public class CopyFailedException extends FinalizeUploadException {
    public CopyFailedException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public CopyFailedException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }

    public CopyFailedException(HttpStatus httpStatus, String message) {
        super(httpStatus, message);
    }

    public CopyFailedException(HttpStatus httpStatus, String message, Throwable cause) {
        super(httpStatus, message, cause);
    }
}
