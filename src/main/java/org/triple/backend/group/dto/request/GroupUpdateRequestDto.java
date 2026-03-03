package org.triple.backend.group.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.triple.backend.group.entity.group.GroupKind;

public record GroupUpdateRequestDto(

        @NotNull(message = "그룹 종류는 필수입니다.")
        GroupKind groupKind,

        @NotBlank(message = "그룹 이름은 필수입니다.")
        String name,

        @NotBlank(message = "그룹 설명은 필수입니다.")
        String description,

        String thumbNailUrl,

        @Min(value = 1, message = "최대 인원은 1명 이상이어야 합니다.")
        @Max(value = 50, message = "최대 인원은 50명 이하여야 합니다.")
        int memberLimit
) {
}
