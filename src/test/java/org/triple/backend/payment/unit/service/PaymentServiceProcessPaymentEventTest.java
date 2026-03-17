package org.triple.backend.payment.unit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.entity.outbox.PaymentEvent;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.repository.PaymentEventJpaRepository;
import org.triple.backend.payment.repository.PaymentJpaRepository;
import org.triple.backend.payment.service.PaymentService;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceProcessPaymentEventTest {

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private PaymentEventJpaRepository paymentEventJpaRepository;

    @Mock
    private InvoiceUserJpaRepository invoiceUserJpaRepository;

    @Mock
    private InvoiceJpaRepository invoiceJpaRepository;

    @Mock
    private UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentJpaRepository,
                paymentEventJpaRepository,
                invoiceUserJpaRepository,
                invoiceJpaRepository,
                userTravelItineraryJpaRepository
        );
    }

    @Test
    @DisplayName("orderId로 결제를 찾지 못하면 NOT_FOUND_PAYMENT 예외가 발생한다")
    void orderId로_결제를_찾지_못하면_NOT_FOUND_PAYMENT_예외가_발생한다() {
        PaymentConfirmReq req = confirmReq("order-1", "payment-key-1");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPaymentEvent(req, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.NOT_FOUND_PAYMENT));
    }

    @Test
    @DisplayName("유저 또는 청구서가 일치하지 않으면 PAYMENT_NOT_ALLOWED 예외가 발생한다")
    void 유저_또는_청구서가_일치하지_않으면_PAYMENT_NOT_ALLOWED_예외가_발생한다() {
        Payment payment = payment("order-1", 2L, 1L, PaymentStatus.READY);
        PaymentConfirmReq req = confirmReq("order-1", "payment-key-1");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.processPaymentEvent(req, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_NOT_ALLOWED));
    }

    @Test
    @DisplayName("결제 상태가 READY가 아니면 PAYMENT_ALREADY_IS_ACTIVE 예외가 발생한다")
    void 결제_상태가_READY가_아니면_PAYMENT_ALREADY_IS_ACTIVE_예외가_발생한다() {
        Payment payment = payment("order-1", 1L, 1L, PaymentStatus.IN_PROGRESS);
        PaymentConfirmReq req = confirmReq("order-1", "payment-key-1");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.processPaymentEvent(req, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_IS_ACTIVE));
    }

    @Test
    @DisplayName("결제 승인 이벤트 요청이 성공하면 결제는 IN_PROGRESS가 되고 outbox 이벤트를 저장한다")
    void 결제_승인_이벤트_요청이_성공하면_결제는_IN_PROGRESS가_되고_outbox_이벤트를_저장한다() {
        Payment payment = payment("order-1", 1L, 1L, PaymentStatus.READY);
        PaymentConfirmReq req = confirmReq("order-1", "payment-key-1");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        paymentService.processPaymentEvent(req, 1L, 1L);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key-1");

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(paymentEventJpaRepository).save(captor.capture());
        PaymentEvent savedEvent = captor.getValue();
        assertThat(savedEvent.getPaymentEventBody().getOrderId()).isEqualTo("order-1");
        assertThat(savedEvent.getPaymentEventBody().getPaymentKey()).isEqualTo("payment-key-1");
    }

    private PaymentConfirmReq confirmReq(String orderId, String paymentKey) {
        return new PaymentConfirmReq(
                "CARD",
                orderId,
                paymentKey,
                new BigDecimal("10000"),
                "TOSS"
        );
    }

    private Payment payment(String orderId, Long userId, Long invoiceId, PaymentStatus paymentStatus) {
        User user = User.builder().id(userId).build();
        Invoice invoice = Invoice.builder().id(invoiceId).invoiceStatus(InvoiceStatus.CONFIRM).build();

        return Payment.builder()
                .orderId(orderId)
                .user(user)
                .invoice(invoice)
                .paymentStatus(paymentStatus)
                .requestedAmount(new BigDecimal("10000"))
                .build();
    }
}
