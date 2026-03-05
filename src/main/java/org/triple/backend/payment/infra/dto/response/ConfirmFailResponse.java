package org.triple.backend.payment.infra.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmFailResponse(
        String code,
        String message
) {
}
