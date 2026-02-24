package org.triple.backend.file.infra;

public interface S3Bucket {
    PresignedUrl issuePresignedUrl(String pendingKey, String mimeType);

    void validatePendingKey(String pendingKey, Long userId);

    void validateUploadedObject(String pendingKey);

    void copyObject(String sourceKey, String destinationKey);

    void deleteObject(String key);

    void validateContentType(String mimeType);

    String concatUploadPrefix(String uploadedKey);
}
