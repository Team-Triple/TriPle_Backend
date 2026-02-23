package org.triple.backend.group.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.triple.backend.group.entity.group.GroupKind;

public record GroupUpdateRequestDto(

        @NotNull
        GroupKind groupKind,

        @NotBlank
        String name,

        @NotBlank
        String description,

        @NotBlank
        String thumbNailUrl,

        @Min(1)
        @Max(50)
        int memberLimit
) {
}
