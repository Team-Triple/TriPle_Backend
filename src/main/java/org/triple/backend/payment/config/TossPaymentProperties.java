package org.triple.backend.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss.payment")
public record TossPaymentProperties(
        String uri,
        String secret,
        String contentType,
        Long timeout
) {}