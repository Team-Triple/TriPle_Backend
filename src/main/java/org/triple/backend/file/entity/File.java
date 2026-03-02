package org.triple.backend.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.triple.backend.global.common.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class File extends BaseEntity {

    @Id
    @Column(name = "file_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerId;

    @Column(name = "uploaded_url")
    private String uploadedUrl;

    @Builder(access = AccessLevel.PRIVATE)
    private File(Long ownerId, String uploadedUrl) {
        this.ownerId = validateOwnerId(ownerId);
        this.uploadedUrl = validateUploadedUrl(uploadedUrl);
    }

    private static Long validateOwnerId(final Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw new IllegalArgumentException("ownerId는 0보다 커야 합니다.");
        }
        return ownerId;
    }

    private static String validateUploadedUrl(final String uploadedUrl) {
        if (uploadedUrl == null || uploadedUrl.isBlank()) {
            throw new IllegalArgumentException("uploadedUrl은 null이거나 공백일 수 없습니다.");
        }
        if (uploadedUrl.length() > 255) {
            throw new IllegalArgumentException("uploadedUrl 길이는 255자 이하여야 합니다.");
        }
        return uploadedUrl;
    }

    public static File of(Long userId, String uploadedUrl) {
        return File.builder()
                .ownerId(userId)
                .uploadedUrl(uploadedUrl)
                .build();
    }
}
