package org.triple.backend.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentCreateReq(

        @NotNull
        @Positive
        BigDecimal amount,

        @NotBlank
        String name

) {
}
