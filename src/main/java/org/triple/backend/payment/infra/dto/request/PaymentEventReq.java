package org.triple.backend.payment.infra.dto.request;

import java.math.BigDecimal;

import org.triple.backend.payment.entity.outbox.PaymentEventBody;

public record PaymentEventReq(
        String paymentKey,
        String orderId,
        BigDecimal amount
) {
    public static PaymentEventReq from(PaymentEventBody paymentEventBody) {
        return new PaymentEventReq(
                paymentEventBody.getPaymentKey(),
                paymentEventBody.getOrderId(),
                paymentEventBody.getRequestedAmount()
        );
    }
}
