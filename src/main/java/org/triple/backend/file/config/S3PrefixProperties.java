package org.triple.backend.file.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class S3PrefixProperties {
    @NotBlank
    private final String pending;

    @NotBlank
    private final String uploaded;

    public S3PrefixProperties(String pending, String uploaded) {
        this.pending = pending;
        this.uploaded = uploaded;
    }
}
