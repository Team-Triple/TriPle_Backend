package org.triple.backend.payment.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payment.event")
public record PaymentEventProperties(
        @Min(1) int batchSize,
        @Min(1) int maxRetryCount,
        @Min(1) long pollFixedRateMs,
        @Min(1) long retryFixedRateMs,
        @Min(1) int workerCoreSize,
        @Min(1) int workerMaxSize,
        @Min(0) int workerQueueCapacity,
        @Min(1) int retryWorkerCoreSize,
        @Min(1) int retryWorkerMaxSize,
        @Min(0) int retryWorkerQueueCapacity
) {
}
