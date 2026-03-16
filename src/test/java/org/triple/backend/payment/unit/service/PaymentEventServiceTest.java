package org.triple.backend.payment.unit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.payment.config.PaymentEventProperties;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.entity.outbox.Error;
import org.triple.backend.payment.entity.outbox.PaymentEvent;
import org.triple.backend.payment.entity.outbox.PaymentEventBody;
import org.triple.backend.payment.entity.outbox.PaymentEventMeta;
import org.triple.backend.payment.entity.outbox.PaymentEventStatus;
import org.triple.backend.payment.infra.dto.response.PaymentEventFailRes;
import org.triple.backend.payment.infra.dto.response.PaymentEventSuccessRes;
import org.triple.backend.payment.repository.PaymentEventJpaRepository;
import org.triple.backend.payment.repository.PaymentJpaRepository;
import org.triple.backend.payment.service.event.PaymentEventService;
import org.triple.backend.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventServiceTest {

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private PaymentEventJpaRepository paymentEventJpaRepository;

    @Mock
    private InvoiceUserJpaRepository invoiceUserJpaRepository;

    private PaymentEventService paymentEventService;

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
        paymentEventService = new PaymentEventService(
                paymentJpaRepository,
                paymentEventJpaRepository,
                invoiceUserJpaRepository,
                paymentEventProperties
        );
    }

    @Test
    @DisplayName("PENDING 이벤트 조회 시 IN_PROGRESS로 바꾸고 이벤트 바디를 반환한다")
    void PENDING_이벤트_조회_시_IN_PROGRESS로_바꾸고_이벤트_바디를_반환한다() {
        PaymentEvent event1 = paymentEvent("order-1", 0, PaymentEventStatus.PENDING);
        PaymentEvent event2 = paymentEvent("order-2", 0, PaymentEventStatus.PENDING);
        given(paymentEventJpaRepository.findPendingEventsForUpdate(2)).willReturn(List.of(event1, event2));

        List<PaymentEventBody> bodies = paymentEventService.findPendingEvents(2);

        assertThat(bodies).hasSize(2);
        assertThat(bodies).extracting(PaymentEventBody::getOrderId).containsExactly("order-1", "order-2");
        assertThat(event1.getPaymentEventStatus()).isEqualTo(PaymentEventStatus.IN_PROGRESS);
        assertThat(event2.getPaymentEventStatus()).isEqualTo(PaymentEventStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("RETRYABLE 이벤트 조회 시 IN_PROGRESS로 바꾸고 이벤트 바디를 반환한다")
    void RETRYABLE_이벤트_조회_시_IN_PROGRESS로_바꾸고_이벤트_바디를_반환한다() {
        PaymentEvent event1 = paymentEvent("order-1", 1, PaymentEventStatus.RETRYABLE);
        PaymentEvent event2 = paymentEvent("order-2", 2, PaymentEventStatus.RETRYABLE);
        given(paymentEventJpaRepository.findFailedEventsForUpdate(2, 3)).willReturn(List.of(event1, event2));

        List<PaymentEventBody> bodies = paymentEventService.findRetryableEvents(2);

        assertThat(bodies).hasSize(2);
        assertThat(bodies).extracting(PaymentEventBody::getOrderId).containsExactly("order-1", "order-2");
        assertThat(event1.getPaymentEventStatus()).isEqualTo(PaymentEventStatus.IN_PROGRESS);
        assertThat(event2.getPaymentEventStatus()).isEqualTo(PaymentEventStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("성공 이벤트 적용 시 결제는 SUCCESS, 이벤트는 SUCCESS가 된다")
    void 성공_이벤트_적용_시_결제는_SUCCESS_이벤트는_SUCCESS가_된다() {
        Payment payment = payment("order-1", PaymentStatus.IN_PROGRESS);
        PaymentEvent paymentEvent = paymentEvent("order-1", 0, PaymentEventStatus.IN_PROGRESS);
        InvoiceUser invoiceUser = InvoiceUser.create(payment.getInvoice(), payment.getUser(), new BigDecimal("10000"));

        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));
        given(paymentEventJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(paymentEvent));
        given(invoiceUserJpaRepository.findByUserIdAndInvoiceIdAndInvoiceStatusForUpdate(
                eq(1L), eq(1L), eq(InvoiceStatus.CONFIRM)))
                .willReturn(Optional.of(invoiceUser));

        PaymentEventSuccessRes successRes = new PaymentEventSuccessRes(
                "order-1",
                "payment-key-1",
                "DONE",
                new BigDecimal("4000"),
                LocalDateTime.of(2030, 3, 20, 12, 0),
                new PaymentEventSuccessRes.Receipt("https://receipt")
        );

        paymentEventService.applyPaymentEventRes(successRes);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getApprovedAmount()).isEqualByComparingTo("4000");
        assertThat(paymentEvent.getPaymentEventStatus()).isEqualTo(PaymentEventStatus.SUCCESS);
        assertThat(invoiceUser.getRemainAmount()).isEqualByComparingTo("6000");
    }

    @Test
    @DisplayName("재시도 가능한 실패는 최대 재시도 이전에 RETRYABLE로 표시한다")
    void 재시도_가능한_실패는_최대_재시도_이전에_RETRYABLE로_표시한다() {
        Payment payment = payment("order-1", PaymentStatus.IN_PROGRESS);
        PaymentEvent paymentEvent = paymentEvent("order-1", 0, PaymentEventStatus.IN_PROGRESS);
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));
        given(paymentEventJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(paymentEvent));

        PaymentEventFailRes failRes = new PaymentEventFailRes("order-1", Error.NETWORK_TIMEOUT, "timeout");

        paymentEventService.applyPaymentEventRes(failRes);

        assertThat(paymentEvent.getPaymentEventStatus()).isEqualTo(PaymentEventStatus.RETRYABLE);
        assertThat(paymentEvent.getPaymentEventMeta().getRetryCount()).isEqualTo(1);
        assertThat(paymentEvent.getPaymentEventMeta().getLastError()).isEqualTo(Error.NETWORK_TIMEOUT);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("재시도 가능한 실패가 최대 횟수에 도달하면 DEAD로 표시한다")
    void 재시도_가능한_실패가_최대_횟수에_도달하면_DEAD로_표시한다() {
        Payment payment = payment("order-1", PaymentStatus.IN_PROGRESS);
        PaymentEvent paymentEvent = paymentEvent("order-1", 2, PaymentEventStatus.IN_PROGRESS);
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));
        given(paymentEventJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(paymentEvent));

        PaymentEventFailRes failRes = new PaymentEventFailRes("order-1", Error.NETWORK_TIMEOUT, "timeout");

        paymentEventService.applyPaymentEventRes(failRes);

        assertThat(paymentEvent.getPaymentEventStatus()).isEqualTo(PaymentEventStatus.DEAD);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("재시도 불가능한 실패는 FAILED로 표시한다")
    void 재시도_불가능한_실패는_FAILED로_표시한다() {
        Payment payment = payment("order-1", PaymentStatus.IN_PROGRESS);
        PaymentEvent paymentEvent = paymentEvent("order-1", 0, PaymentEventStatus.IN_PROGRESS);
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));
        given(paymentEventJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(paymentEvent));

        PaymentEventFailRes failRes = new PaymentEventFailRes("order-1", Error.UPSTREAM_4XX, "bad request");

        paymentEventService.applyPaymentEventRes(failRes);

        assertThat(paymentEvent.getPaymentEventStatus()).isEqualTo(PaymentEventStatus.FAILED);
        assertThat(paymentEvent.getPaymentEventMeta().getLastError()).isEqualTo(Error.UPSTREAM_4XX);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("비동기 예외 후 재시도 여유가 있으면 RETRYABLE로 표시한다")
    void 비동기_예외_후_재시도_여유가_있으면_RETRYABLE로_표시한다() {
        Payment payment = payment("order-1", PaymentStatus.IN_PROGRESS);
        PaymentEvent paymentEvent = paymentEvent("order-1", 0, PaymentEventStatus.IN_PROGRESS);
        given(paymentEventJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(paymentEvent));
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        paymentEventService.finalizeException("order-1");

        assertThat(paymentEvent.getPaymentEventStatus()).isEqualTo(PaymentEventStatus.RETRYABLE);
        assertThat(paymentEvent.getPaymentEventMeta().getLastError()).isEqualTo(Error.UNKNOWN);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("비동기 예외 후 재시도 횟수를 초과하면 DEAD로 표시한다")
    void 비동기_예외_후_재시도_횟수를_초과하면_DEAD로_표시한다() {
        Payment payment = payment("order-1", PaymentStatus.IN_PROGRESS);
        PaymentEvent paymentEvent = paymentEvent("order-1", 2, PaymentEventStatus.IN_PROGRESS);
        given(paymentEventJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(paymentEvent));
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        paymentEventService.finalizeException("order-1");

        assertThat(paymentEvent.getPaymentEventStatus()).isEqualTo(PaymentEventStatus.DEAD);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("비동기 예외 처리 시 이벤트가 없으면 무시한다")
    void 비동기_예외_처리_시_이벤트가_없으면_무시한다() {
        given(paymentEventJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.empty());

        paymentEventService.finalizeException("order-1");

        verify(paymentJpaRepository, never()).findByOrderIdForUpdate("order-1");
    }

    @Test
    @DisplayName("비동기 예외 처리 시 이벤트 상태가 IN_PROGRESS가 아니면 무시한다")
    void 비동기_예외_처리_시_이벤트_상태가_IN_PROGRESS가_아니면_무시한다() {
        PaymentEvent paymentEvent = paymentEvent("order-1", 1, PaymentEventStatus.RETRYABLE);
        given(paymentEventJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(paymentEvent));

        paymentEventService.finalizeException("order-1");

        verify(paymentJpaRepository, never()).findByOrderIdForUpdate("order-1");
        assertThat(paymentEvent.getPaymentEventStatus()).isEqualTo(PaymentEventStatus.RETRYABLE);
    }

    private Payment payment(String orderId, PaymentStatus paymentStatus) {
        User user = User.builder().id(1L).build();
        Invoice invoice = Invoice.builder().id(1L).invoiceStatus(InvoiceStatus.CONFIRM).build();

        return Payment.builder()
                .orderId(orderId)
                .paymentKey("payment-key-1")
                .requestedAmount(new BigDecimal("10000"))
                .paymentStatus(paymentStatus)
                .invoice(invoice)
                .user(user)
                .build();
    }

    private PaymentEvent paymentEvent(String orderId, int retryCount, PaymentEventStatus status) {
        return PaymentEvent.builder()
                .paymentEventStatus(status)
                .paymentEventBody(PaymentEventBody.builder()
                        .orderId(orderId)
                        .paymentKey("payment-key-1")
                        .requestedAmount(new BigDecimal("10000"))
                        .build())
                .paymentEventMeta(PaymentEventMeta.builder()
                        .retryCount(retryCount)
                        .build())
                .build();
    }
}
