package org.triple.backend.file.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;

@Getter
public class S3PresignProperties {
    @Min(1)
    private final int putExpireSeconds;

    public S3PresignProperties(int putExpireSeconds) {
        this.putExpireSeconds = putExpireSeconds;
    }
}
