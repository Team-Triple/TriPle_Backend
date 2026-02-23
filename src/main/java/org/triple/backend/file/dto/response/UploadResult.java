package org.triple.backend.file.dto.response;

import org.springframework.http.HttpStatus;

public record UploadResult(
        String pendingKey,
        String uploadedKey,
        boolean success,
        HttpStatus httpStatus,
        String message
) {
    public static UploadResult success(String pendingKey, String uploadedKey) {
        return new UploadResult(pendingKey, uploadedKey, true, null, null);
    }

    public static UploadResult fail(String pendingKey, HttpStatus httpStatus, String message) {
        return new UploadResult(pendingKey, null, false, httpStatus, message);
    }
}
