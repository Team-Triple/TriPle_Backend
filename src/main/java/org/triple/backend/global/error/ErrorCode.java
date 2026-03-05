package org.triple.backend.global.error;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    HttpStatus getStatus();
    String getMessage();
}
