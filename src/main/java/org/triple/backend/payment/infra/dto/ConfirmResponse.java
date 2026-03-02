package org.triple.backend.payment.infra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmResponse(
    String orderId,
    String paymentKey,
    String status,
    BigDecimal totalAmount,
    Receipt receipt
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Receipt(String url) {}
}
