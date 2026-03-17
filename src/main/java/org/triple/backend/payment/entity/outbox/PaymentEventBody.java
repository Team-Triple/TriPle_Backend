package org.triple.backend.payment.entity.outbox;

import jakarta.persistence.Embeddable;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.triple.backend.payment.entity.Payment;

import java.math.BigDecimal;

@Getter
@Embeddable
@NoArgsConstructor
public class PaymentEventBody {
    private String paymentKey;

    private String orderId;

    private BigDecimal requestedAmount;

    @Builder
    public PaymentEventBody(String paymentKey, String orderId, BigDecimal requestedAmount) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.requestedAmount = requestedAmount;
    }

    public static PaymentEventBody create(Payment payment) {
        return new PaymentEventBody(
                payment.getPaymentKey(),
                payment.getOrderId(),
                payment.getRequestedAmount()
        );
    }
}
