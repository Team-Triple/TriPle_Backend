package org.triple.backend.invoice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecipientAmountDto(
        @NotNull
        Long userId,

        @NotNull
        @Positive
        BigDecimal amount
) {
}
