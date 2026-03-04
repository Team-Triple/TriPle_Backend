package org.triple.backend.file.config.property;

import jakarta.validation.constraints.NotBlank;

public record S3PrefixProperties(
        @NotBlank String pending,
        @NotBlank String uploaded,
        @NotBlank String getUrlPrefix
) {}
