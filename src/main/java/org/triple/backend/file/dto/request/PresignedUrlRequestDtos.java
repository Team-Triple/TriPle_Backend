package org.triple.backend.file.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PresignedUrlRequestDtos(
        @NotEmpty
        @Size(max = 20)
        List<@Valid PresignedUrlRequestDto> presignedUrlRequestDtos
) {
}
