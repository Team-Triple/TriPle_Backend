package org.triple.backend.payment.infra.dto;

import java.math.BigDecimal;
import org.triple.backend.payment.entity.Payment;

public record ConfirmRequest(
    String paymentKey,
    String orderId,
    BigDecimal amount
) {
    public static ConfirmRequest from(Payment payment) {
        return new ConfirmRequest(
            payment.getPaymentKey(),
            payment.getOrderId(),
            payment.getApprovedAmount()
        );
    }
}
