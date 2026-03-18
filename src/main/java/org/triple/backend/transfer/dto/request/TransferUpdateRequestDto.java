package org.triple.backend.transfer.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TransferUpdateRequestDto(

        @NotBlank(message = "청구서 제목은 필수입니다.")
        String title,

        @NotBlank(message = "청구서 설명은 필수입니다.")
        String description,

        @Future(message = "납부 기한은 현재 시각 이후여야 합니다.")
        @NotNull(message = "납부 기한은 필수입니다.")
        LocalDateTime dueAt
) {
}
