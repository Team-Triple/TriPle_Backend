package org.triple.backend.file.infra.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class FinalizeUploadException extends RuntimeException {
    private final HttpStatus httpStatus;

    public FinalizeUploadException(HttpStatus httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public FinalizeUploadException(HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}
