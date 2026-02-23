package org.triple.backend.file.infra.exception;

import org.springframework.http.HttpStatus;

public class UploadFailedException extends FinalizeUploadException {
    public UploadFailedException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public UploadFailedException(String message, Throwable cause) {
        super(HttpStatus.NOT_FOUND, message, cause);
    }

    public UploadFailedException(HttpStatus httpStatus, String message) {
        super(httpStatus, message);
    }

    public UploadFailedException(HttpStatus httpStatus, String message, Throwable cause) {
        super(httpStatus, message, cause);
    }
}
