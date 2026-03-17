package org.triple.backend.payment.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.triple.backend.payment.config.PaymentEventProperties;
import org.triple.backend.payment.entity.outbox.PaymentEventBody;
import org.triple.backend.payment.infra.TossPayment;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventScheduler {

    private final TossPayment tossPayment;
    private final PaymentEventService paymentEventService;
    private final PaymentEventProperties paymentEventProperties;
    private final TaskExecutor paymentEventExecutor;
    private final TaskExecutor paymentEventExecutorRetry;

    @Scheduled(fixedRateString = "${payment.event.poll-fixed-rate-ms}")
    public void paymentEventTask() {
        List<PaymentEventBody> paymentEventBodies = paymentEventService.findPendingEvents(paymentEventProperties.batchSize());
        paymentEventBodies.forEach(this::processPaymentEvent);
    }

    private void processPaymentEvent(PaymentEventBody paymentEventBody) {
        CompletableFuture
                .supplyAsync(() -> tossPayment.request(paymentEventBody), paymentEventExecutor)
                .thenAccept(paymentEventService::applyPaymentEventRes)
                .exceptionally(exception -> {
                    log.error("결제 승인 처리 중 장애가 발생했습니다.", exception);
                    paymentEventService.finalizeException(paymentEventBody.getOrderId());
                    return null;
                });
    }

    @Scheduled(fixedRateString = "${payment.event.retry-fixed-rate-ms}")
    public void paymentEventRetryTask() {
        List<PaymentEventBody> paymentEventBodies = paymentEventService.findRetryableEvents(paymentEventProperties.batchSize());
        paymentEventBodies.forEach(this::processPaymentEventRetry);
    }

    private void processPaymentEventRetry(PaymentEventBody paymentEventBody) {
        CompletableFuture
                .supplyAsync(() -> tossPayment.request(paymentEventBody), paymentEventExecutorRetry)
                .thenAccept(paymentEventService::applyPaymentEventRes)
                .exceptionally(exception -> {
                    log.error("결제 승인 처리 중 장애가 발생했습니다.", exception);
                    paymentEventService.finalizeException(paymentEventBody.getOrderId());
                    return null;
                });
    }
}
