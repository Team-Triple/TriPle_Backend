package org.triple.backend.payment.dto.response;

import java.math.BigDecimal;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;

public record PaymentConfirmRes(
    String orderId,
    BigDecimal amount,
    String receiptUrl,
    PaymentStatus status
) {
    public static PaymentConfirmRes from(Payment payment) {
        return new PaymentConfirmRes(
            payment.getOrderId(),
            payment.getApprovedAmount(),
            payment.getReceiptUrl(),
            payment.getPaymentStatus()
        );
    }
}
