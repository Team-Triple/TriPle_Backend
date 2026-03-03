package org.triple.backend.payment.dto.response;

import java.math.BigDecimal;

public record PaymentCreateRes(
        String orderId,
        String orderName,
        BigDecimal amount
) {
}
