package org.triple.backend.payment.entity.outbox;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Embeddable
@NoArgsConstructor
public class PaymentEventMeta {
    private Integer retryCount;

    private Error lastError;

    private LocalDateTime lastEventTime;

    @Builder
    public PaymentEventMeta(Integer retryCount, Error lastError, LocalDateTime lastRetryTime) {
        this.retryCount = retryCount;
        this.lastError = lastError;
        this.lastEventTime = lastRetryTime;
    }

    public static PaymentEventMeta create() {
        return PaymentEventMeta.builder()
                .retryCount(0)
                .build();
    }

    public void markRetryable(Error error, LocalDateTime lastEventTime) {
        this.retryCount = (this.retryCount == null) ? 1 : this.retryCount + 1;
        this.lastError = error;
        this.lastEventTime = lastEventTime;
    }

    public void markNonRetryable(Error error, LocalDateTime lastEventTime) {
        this.retryCount = (this.retryCount == null) ? 1 : this.retryCount + 1;
        this.lastError = error;
        this.lastEventTime = lastEventTime;
    }
}
