package org.triple.backend.payment.dto.request;

import java.math.BigDecimal;

public record PaymentConfirmReq(
    String method,
    String orderId,
    String paymentKey,
    BigDecimal approvedAmount,
    String pgProvider
) {}
