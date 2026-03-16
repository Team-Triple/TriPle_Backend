package org.triple.backend.payment.infra.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.triple.backend.payment.entity.outbox.Error;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEventFailRes(
        String orderId,
        Error error,
        String message
) implements PaymentEventRes {
}
