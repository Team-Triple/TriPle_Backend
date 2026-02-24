package org.triple.backend.file.dto.response;

import org.springframework.http.HttpStatus;

public record UploadResult(
        String pendingKey,
        String uploadedKey,
        String uploadedUrl,
        boolean success,
        HttpStatus httpStatus,
        String message
) {
    public static UploadResult success(String pendingKey, String uploadedKey, String uploadedUrl) {
        return new UploadResult(pendingKey, uploadedKey, uploadedUrl,true, null, null);
    }

    public static UploadResult fail(String pendingKey, HttpStatus httpStatus, String message) {
        return new UploadResult(pendingKey, null, null, false, httpStatus, message);
    }
}
