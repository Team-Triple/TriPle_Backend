package org.triple.backend.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PresignedUrlRequestDto(
        @NotBlank(message = "파일명은 필수입니다.")
        @Pattern(
                regexp = "(?i)^[^/\\\\]+\\.(jpg|jpeg|png)$",
                message = "파일명은 jpg, jpeg, png 확장자만 허용됩니다."
        )
        String fileName,

        @NotBlank(message = "mimeType은 필수입니다.")
        String mimeType //image/jpg 이런 식의 타입
) {
}
