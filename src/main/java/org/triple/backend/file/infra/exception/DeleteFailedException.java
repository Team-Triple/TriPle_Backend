package org.triple.backend.file.infra.exception;

import org.springframework.http.HttpStatus;

public class DeleteFailedException extends FinalizeUploadException {
    public DeleteFailedException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public DeleteFailedException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }

    public DeleteFailedException(HttpStatus httpStatus, String message) {
        super(httpStatus, message);
    }

    public DeleteFailedException(HttpStatus httpStatus, String message, Throwable cause) {
        super(httpStatus, message, cause);
    }
}
