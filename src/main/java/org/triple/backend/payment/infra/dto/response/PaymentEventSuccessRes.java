package org.triple.backend.payment.infra.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEventSuccessRes(
        String orderId,
        String paymentKey,
        String status,
        BigDecimal totalAmount,
        LocalDateTime approvedAt,
        Receipt receipt
) implements PaymentEventRes {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Receipt(String url) {}
}
