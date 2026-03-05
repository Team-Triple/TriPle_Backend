package org.triple.backend.file.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.triple.backend.file.config.property.S3BucketProperties;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BucketKeyPublisher {
    private final S3BucketProperties s3BucketProperties;

    public String publishPendingKey(String fileName, Long userId) {
        String extension = getExtension(fileName);
        return s3BucketProperties.prefix().pending() + userId + "/" + getUUID() + "." + extension;
    }

    public String publishUploadedKey(String pendingKey) {
        if (pendingKey == null || pendingKey.isBlank()) {
            throw new IllegalArgumentException("pendingKey는 null이거나 공백일 수 없습니다.");
        }

        String pendingPrefix = s3BucketProperties.prefix().pending();
        String uploadedPrefix = s3BucketProperties.prefix().uploaded();

        if (!pendingKey.startsWith(pendingPrefix)) {
            throw new IllegalArgumentException("pendingKey는 pending prefix로 시작해야 합니다.");
        }

        String keyWithoutPrefix = pendingKey.substring(pendingPrefix.length());

        if (keyWithoutPrefix.isBlank() || !keyWithoutPrefix.contains("/")) {
            throw new IllegalArgumentException("pendingKey 형식이 올바르지 않습니다.");
        }

        return uploadedPrefix + keyWithoutPrefix;
    }

    private String getUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName은 null이거나 공백일 수 없습니다.");
        }
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart <= 0 || extensionStart == fileName.length() - 1) {
            throw new IllegalArgumentException("fileName에는 확장자가 포함되어야 합니다.");
        }
        return fileName.substring(extensionStart + 1);
    }
}
