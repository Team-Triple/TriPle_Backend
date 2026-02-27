package org.triple.backend.invoice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.triple.backend.invoice.dto.RecipientAmountDto;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceAdjustRequestDto(
    @NotNull
    @Positive
    BigDecimal totalAmount,

    @NotEmpty
    List<@Valid RecipientAmountDto> recipients
) {
}
