package org.triple.backend.file.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PresignedUrlRequestDtos(
        @NotEmpty(message = "요청 목록은 비어 있을 수 없습니다.")
        @Size(max = 20, message = "요청 목록은 최대 20개까지 가능합니다.")
        List<@Valid PresignedUrlRequestDto> presignedUrlRequestDtos
) {
}
