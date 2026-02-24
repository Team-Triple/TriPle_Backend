package org.triple.backend.file.dto.response;

import org.springframework.http.HttpStatus;

public record UploadFailed(
    String pendingKey,
    HttpStatus httpStatus,
    String message
) implements UploadResult{}
