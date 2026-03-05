package org.triple.backend.file.dto.response;

public record UploadSuccess(
    String pendingKey,
    String uploadedKey,
    String uploadedUrl
) implements UploadResult{}
