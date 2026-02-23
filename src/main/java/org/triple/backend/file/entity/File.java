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

    @Column(name = "file_key")
    private String key;

    @Builder(access = AccessLevel.PRIVATE)
    private File(
            Long ownerId,
            String key
    ) {
        this.ownerId = validateOwnerId(ownerId);
        this.key = validateKey(key);
    }

    private static Long validateOwnerId(final Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw new IllegalArgumentException("ownerId는 0보다 커야 합니다.");
        }
        return ownerId;
    }

    private static String validateKey(final String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key는 null이거나 공백일 수 없습니다.");
        }
        if (key.length() > 255) {
            throw new IllegalArgumentException("key 길이는 255자를 초과할 수 없습니다.");
        }
        return key;
    }

    public static File of(Long userId, String uploadedKey) {
        return File.builder()
                .ownerId(userId)
                .key(uploadedKey)
                .build();
    }
}
