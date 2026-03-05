package org.triple.backend.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UploadedKeysRequestDto(
        @NotEmpty(message = "업로드 완료 키 목록은 비어 있을 수 없습니다.")
        @Size(max = 20, message = "업로드 완료 키 목록은 최대 20개까지 가능합니다.")
        List<
                @NotBlank(message = "업로드 완료 키는 공백일 수 없습니다.")
                @Pattern(
                        regexp = "(?i)^[a-z0-9/_-]+\\.(jpg|jpeg|png)$",
                        message = "업로드 완료 키 형식이 올바르지 않습니다."
                )
                String
        > keys
) {
}
