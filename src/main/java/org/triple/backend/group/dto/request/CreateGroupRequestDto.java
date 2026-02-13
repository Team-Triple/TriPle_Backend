package org.triple.backend.group.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.triple.backend.group.entity.group.GroupKind;

public record CreateGroupRequestDto(
        @NotBlank
        String name,

        @NotBlank
        String description,

        @Min(1)
        int memberLimit,

        @NotNull
        GroupKind groupKind,

        @NotBlank
        String thumbNailUrl
) {
}