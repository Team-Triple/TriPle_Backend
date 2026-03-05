package org.triple.backend.file.config.property;

import jakarta.validation.constraints.Min;

public record S3PresignProperties(
        @Min(1) int putExpireSeconds
) {}
