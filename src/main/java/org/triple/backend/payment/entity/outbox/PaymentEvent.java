package org.triple.backend.payment.entity.outbox;

import jakarta.persistence.*;
import lombok.*;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.payment.entity.Payment;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payment_event")
public class PaymentEvent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_event_id")
    private Long id;

    @Embedded
    private PaymentEventMeta paymentEventMeta;

    @Embedded
    private PaymentEventBody paymentEventBody;

    @Enumerated(EnumType.STRING)
    private PaymentEventStatus paymentEventStatus;

    @Builder
    public PaymentEvent(PaymentEventBody paymentEventBody, PaymentEventStatus paymentEventStatus, PaymentEventMeta paymentEventMeta) {
        this.paymentEventBody = paymentEventBody;
        this.paymentEventStatus = paymentEventStatus;
        this.paymentEventMeta = paymentEventMeta;
    }

    public static PaymentEvent create(Payment payment) {
        return PaymentEvent.builder()
                .paymentEventBody(PaymentEventBody.create(payment))
                .paymentEventStatus(PaymentEventStatus.PENDING)
                .paymentEventMeta(PaymentEventMeta.create())
                .build();
    }

    public void markSuccess() {
        this.paymentEventStatus = PaymentEventStatus.SUCCESS;
    }

    public void markRetryable(Error error, LocalDateTime lastEventTime) {
        this.paymentEventMeta.updateRetry(error, lastEventTime);
        this.paymentEventStatus = PaymentEventStatus.RETRYABLE;
    }

    public void markFailed(Error error, LocalDateTime lastEventTime) {
        this.paymentEventMeta.updateRetry(error, lastEventTime);
        this.paymentEventStatus = PaymentEventStatus.FAILED;
    }

    public void markDead(Error error, LocalDateTime lastEventTime) {
        this.paymentEventMeta.updateRetry(error, lastEventTime);
        this.paymentEventStatus = PaymentEventStatus.DEAD;
    }

    public boolean isRetryCountExceeded(int maxRetryCount) {
        Integer retryCount = this.paymentEventMeta.getRetryCount();
        if (retryCount == null) {
            return false;
        }
        return retryCount >= maxRetryCount;
    }

    public void updatePaymentEventStatus(PaymentEventStatus paymentEventStatus) {
        this.paymentEventStatus = paymentEventStatus;
    }
}
