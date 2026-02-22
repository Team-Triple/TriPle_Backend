package org.triple.backend.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PresignedUrlRequestDto(
        @NotBlank
        @Pattern(regexp = "(?i)^[^/\\\\]+\\.(jpg|jpeg|png)$")
        String fileName,

        @NotBlank
        String mimeType //image/jpg 이런 식의 타입
) {
}
