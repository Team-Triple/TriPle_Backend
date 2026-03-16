package org.triple.backend.payment.unit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.triple.backend.payment.config.PaymentEventProperties;
import org.triple.backend.payment.entity.outbox.Error;
import org.triple.backend.payment.entity.outbox.PaymentEventBody;
import org.triple.backend.payment.infra.TossPayment;
import org.triple.backend.payment.infra.dto.response.PaymentEventFailRes;
import org.triple.backend.payment.service.event.PaymentEventScheduler;
import org.triple.backend.payment.service.event.PaymentEventService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventSchedulerTest {

    @Mock
    private TossPayment tossPayment;

    @Mock
    private PaymentEventService paymentEventService;

    private PaymentEventScheduler paymentEventScheduler;

    @BeforeEach
    void setUp() {
        PaymentEventProperties paymentEventProperties = new PaymentEventProperties(
                20,
                3,
                1000,
                1000,
                10,
                10,
                100,
                10,
                10,
                100
        );
        TaskExecutor directExecutor = Runnable::run;
        paymentEventScheduler = new PaymentEventScheduler(
                tossPayment,
                paymentEventService,
                paymentEventProperties,
                directExecutor,
                directExecutor
        );
    }

    @Test
    @DisplayName("스케줄러는 PENDING 이벤트를 조회해 처리한다")
    void 스케줄러는_PENDING_이벤트를_조회해_처리한다() {
        PaymentEventBody body = eventBody("order-1");
        PaymentEventFailRes response = new PaymentEventFailRes("order-1", Error.UPSTREAM_4XX, "bad request");

        given(paymentEventService.findPendingEvents(20)).willReturn(List.of(body));
        given(tossPayment.request(body)).willReturn(response);

        paymentEventScheduler.paymentEventTask();

        verify(paymentEventService).findPendingEvents(20);
        verify(tossPayment).request(body);
        verify(paymentEventService).applyPaymentEventRes(response);
    }

    @Test
    @DisplayName("PENDING 이벤트 비동기 처리 중 예외가 나면 finalizeException을 호출한다")
    void PENDING_이벤트_비동기_처리_중_예외가_나면_finalizeException을_호출한다() {
        PaymentEventBody body = eventBody("order-1");

        given(paymentEventService.findPendingEvents(20)).willReturn(List.of(body));
        given(tossPayment.request(body)).willThrow(new RuntimeException("boom"));

        paymentEventScheduler.paymentEventTask();

        verify(paymentEventService).finalizeException("order-1");
    }

    @Test
    @DisplayName("재시도 스케줄러는 RETRYABLE 이벤트를 조회해 처리한다")
    void 재시도_스케줄러는_RETRYABLE_이벤트를_조회해_처리한다() {
        PaymentEventBody body = eventBody("order-2");
        PaymentEventFailRes response = new PaymentEventFailRes("order-2", Error.NETWORK_TIMEOUT, "timeout");

        given(paymentEventService.findRetryableEvents(20)).willReturn(List.of(body));
        given(tossPayment.request(body)).willReturn(response);

        paymentEventScheduler.paymentEventRetryTask();

        verify(paymentEventService).findRetryableEvents(20);
        verify(tossPayment).request(body);
        verify(paymentEventService).applyPaymentEventRes(response);
    }

    @Test
    @DisplayName("RETRYABLE 이벤트 비동기 처리 중 예외가 나면 finalizeException을 호출한다")
    void RETRYABLE_이벤트_비동기_처리_중_예외가_나면_finalizeException을_호출한다() {
        PaymentEventBody body = eventBody("order-2");

        given(paymentEventService.findRetryableEvents(20)).willReturn(List.of(body));
        given(tossPayment.request(body)).willThrow(new RuntimeException("boom"));

        paymentEventScheduler.paymentEventRetryTask();

        verify(paymentEventService).finalizeException("order-2");
    }

    private PaymentEventBody eventBody(String orderId) {
        return PaymentEventBody.builder()
                .orderId(orderId)
                .paymentKey("payment-key")
                .requestedAmount(new BigDecimal("1000"))
                .build();
    }
}
