package org.triple.backend.payment.dto.response;

import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentMethod;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.entity.PgProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentCursorRes(
        List<PaymentSummaryDto> items,
        Long nextCursor,
        boolean hasNext
) {

    public record PaymentSummaryDto(
            Long paymentId,
            Long invoiceId,
            String name,
            PgProvider pgProvider,
            PaymentMethod method,
            PaymentStatus status,
            BigDecimal requestedAmount,
            BigDecimal approvedAmount,
            LocalDateTime requestedAt,
            LocalDateTime approvedAt
    ) {}

    public static PaymentCursorRes from(final List<Payment> payments, final Long cursor, final boolean hasNext) {

        List<PaymentSummaryDto> res = payments.stream().map(r -> new PaymentSummaryDto(
                r.getId(),
                r.getInvoice().getId(),
                r.getName(),
                r.getPgProvider(),
                r.getMethod(),
                r.getPaymentStatus(),
                r.getRequestedAmount(),
                r.getApprovedAmount(),
                r.getRequestedAt(),
                r.getApprovedAt()
        )).toList();

        return new PaymentCursorRes(res, cursor, hasNext);
    }
}
