package org.triple.backend.transfer.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TransferUpdateRequestDto(

        @Future(message = "납부 기한은 현재 시각 이후여야 합니다.")
        @NotNull(message = "납부 기한은 필수입니다.")
        LocalDateTime dueAt
) {
}
