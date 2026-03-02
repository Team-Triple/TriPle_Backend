package org.triple.backend.invoice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.triple.backend.invoice.dto.RecipientAmountDto;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceAdjustRequestDto(
    @NotNull(message = "총 청구 금액은 필수입니다.")
    @Positive(message = "총 청구 금액은 0보다 커야 합니다.")
    BigDecimal totalAmount,

    @NotEmpty(message = "청구 대상 목록은 비어 있을 수 없습니다.")
    List<@Valid RecipientAmountDto> recipients
) {
}
