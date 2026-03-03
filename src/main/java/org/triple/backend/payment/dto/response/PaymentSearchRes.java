package org.triple.backend.payment.dto.response;

import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentSearchRes(
        Long invoiceId,
        List<PaymentDetail> payments
) {

    public static PaymentSearchRes from(final Long invoiceId, final List<Payment> payments) {
        return new PaymentSearchRes(
                invoiceId,
                payments.stream()
                        .map(PaymentDetail::from)
                        .toList()
        );
    }

    public record PaymentDetail(
            Long userId,
            String userNickname,
            String orderId,
            BigDecimal requestedAmount,
            PaymentStatus paymentStatus,
            LocalDateTime requestedAt
    ) {
        public static PaymentDetail from(final Payment payment) {
            return new PaymentDetail(
                    payment.getUser().getId(),
                    payment.getUser().getNickname(),
                    payment.getOrderId(),
                    payment.getRequestedAmount(),
                    payment.getPaymentStatus(),
                    payment.getRequestedAt()
            );
        }
    }
}
