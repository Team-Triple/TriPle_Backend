package org.triple.backend.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecipientAmountDto(
        @NotBlank(message = "청구 대상 사용자 ID는 필수입니다.")
        String userId,

        @NotNull(message = "청구 금액은 필수입니다.")
        @Positive(message = "청구 금액은 0보다 커야 합니다.")
        BigDecimal amount
) {
}
