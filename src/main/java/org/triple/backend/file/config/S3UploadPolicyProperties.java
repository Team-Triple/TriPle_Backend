package org.triple.backend.file.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

@Getter
public class S3UploadPolicyProperties {
    @NotEmpty
    private final List<@NotBlank String> allowedExtensions;

    @NotEmpty
    private final List<@NotBlank String> allowedContentTypes;

    public S3UploadPolicyProperties(
            List<String> allowedExtensions,
            List<String> allowedContentTypes
    ) {
        this.allowedExtensions = allowedExtensions == null ? null : List.copyOf(allowedExtensions);
        this.allowedContentTypes = allowedContentTypes == null ? null : List.copyOf(allowedContentTypes);
    }
}
