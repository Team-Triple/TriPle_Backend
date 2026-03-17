package org.triple.backend.payment.entity.outbox;

public enum PaymentEventStatus {
    PENDING,    //아직 토스 호출 X
    IN_PROGRESS,
    SUCCESS,
    RETRYABLE,
    FAILED,
    DEAD        //3번 모두 타임아웃
}
