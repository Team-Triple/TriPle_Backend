package org.triple.backend.file.config.property;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record S3UploadPolicyProperties(
        @NotEmpty
        List<@NotBlank String> allowedExtensions,
        @NotEmpty
        List<@NotBlank String> allowedContentTypes
) {
}
