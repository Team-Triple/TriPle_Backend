package org.triple.backend.invoice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record InvoiceUpdateRequestDto(

        @NotBlank
        String title,

        @NotBlank
        String description,

        @NotNull
        LocalDateTime dueAt
) {
}
