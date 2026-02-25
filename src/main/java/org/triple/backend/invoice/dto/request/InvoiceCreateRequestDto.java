package org.triple.backend.invoice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceCreateRequestDto(

        @NotNull
        Long groupId,

        @NotNull
        Long travelItineraryId,

        @NotEmpty
        List<@Valid RecipientAmountDto> recipients,

        @NotBlank
        @Size(max = 50)
        String title,

        @NotBlank
        String description,

        @NotNull
        @Positive
        BigDecimal totalAmount,

        @NotNull
        @Future
        LocalDateTime dueAt
) {

    public record RecipientAmountDto(
            @NotNull
            Long userId,

            @NotNull
            @Positive
            BigDecimal amount
    ){}
}
