package org.triple.backend.file.infra;

import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Instant;

public final class PresignedUrl {
    private final String key;
    private final PresignedPutObjectRequest presignedPutObjectRequest;

    public PresignedUrl(String key, PresignedPutObjectRequest presignedPutObjectRequest) {
        this.key = validateKey(key);
        this.presignedPutObjectRequest = validatePresignedPutObjectRequest(presignedPutObjectRequest);
    }

    public String key() {
        return key;
    }

    public String presignedUrl() {
        if (presignedPutObjectRequest.url() == null) {
            throw new NullPointerException("presigned URL이 비어 있습니다.");
        }
        return presignedPutObjectRequest.url().toString();
    }

    public Instant expiresAt() {
        if (presignedPutObjectRequest.expiration() == null) {
            throw new NullPointerException("만료 시간이 비어 있습니다.");
        }
        return presignedPutObjectRequest.expiration();
    }

    private static String validateKey(final String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key는 null이거나 공백일 수 없습니다.");
        }
        return key;
    }

    private static PresignedPutObjectRequest validatePresignedPutObjectRequest(
            final PresignedPutObjectRequest presignedPutObjectRequest
    ) {
        if (presignedPutObjectRequest == null) {
            throw new IllegalArgumentException("presignedPutObjectRequest는 null일 수 없습니다.");
        }
        return presignedPutObjectRequest;
    }
}
