package org.triple.backend.payment.unit.service;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.infra.TossPayment;
import org.triple.backend.payment.infra.dto.ConfirmResponse;
import org.triple.backend.payment.repository.PaymentJpaRepository;
import org.triple.backend.payment.service.PaymentService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private TossPayment tossPayment;

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("READY 결제를 IN_PROGRESS 상태로 변경한다")
    void READY_결제를_IN_PROGRESS_상태로_변경한다() {
        Payment payment = payment("order-1", PaymentStatus.READY, "10000");
        PaymentConfirmReq request = confirmReq("order-1", "10000");
        given(paymentJpaRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        Payment result = paymentService.readyToInProgressPayment(request, 1L, 1L);

        assertThat(result).isSameAs(payment);
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("orderId로 결제를 찾지 못하면 예외가 발생한다")
    void orderId로_결제를_찾지_못하면_예외가_발생한다() {
        PaymentConfirmReq request = confirmReq("missing-order", "10000");
        given(paymentJpaRepository.findByOrderId("missing-order")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.readyToInProgressPayment(request, 1L, 1L))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.NOT_FOUND_PAYMENT));
    }

    @Test
    @DisplayName("결제 상태가 READY가 아니면 ALREADY_PROCESSED_PAYMENT 예외가 발생한다")
    void 결제_상태가_READY가_아니면_ALREADY_PROCESSED_PAYMENT_예외가_발생한다() {
        Payment payment = payment("order-1", PaymentStatus.DONE, "10000");
        PaymentConfirmReq request = confirmReq("order-1", "10000");
        given(paymentJpaRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.readyToInProgressPayment(request, 1L, 1L))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT));
    }

    @Test
    @DisplayName("승인 금액이 일치하지 않으면 ILLEGAL_AMOUNT 예외가 발생한다")
    void 승인_금액이_일치하지_않으면_ILLEGAL_AMOUNT_예외가_발생한다() {
        Payment payment = payment("order-1", PaymentStatus.READY, "10000");
        PaymentConfirmReq request = confirmReq("order-1", "9000");
        given(paymentJpaRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.readyToInProgressPayment(request, 1L, 1L))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.ILLEGAL_AMOUNT));
    }

    @Test
    @DisplayName("confirm 호출은 TossPayment로 위임한다")
    void confirm_호출은_TossPayment로_위임한다() {
        Payment payment = payment("order-1", PaymentStatus.IN_PROGRESS, "10000");
        ConfirmResponse response = new ConfirmResponse(
            "order-1",
            "payment-key",
            "DONE",
            new BigDecimal("10000"),
            new ConfirmResponse.Receipt("https://receipt.url")
        );
        given(tossPayment.confirmRequest(payment)).willReturn(response);

        ConfirmResponse result = paymentService.confirm(payment);

        assertThat(result).isSameAs(response);
        verify(tossPayment).confirmRequest(payment);
    }

    @Test
    @DisplayName("IN_PROGRESS 결제를 DONE 상태로 변경하고 영수증 URL을 저장한다")
    void IN_PROGRESS_결제를_DONE_상태로_변경하고_영수증_URL을_저장한다() {
        Payment payment = payment("order-1", PaymentStatus.IN_PROGRESS, "10000");
        ConfirmResponse response = new ConfirmResponse(
            "order-1",
            "payment-key",
            "DONE",
            new BigDecimal("10000"),
            new ConfirmResponse.Receipt("https://receipt.url")
        );
        given(paymentJpaRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        Payment result = paymentService.inprogressToDonePayment(response);

        assertThat(result).isSameAs(payment);
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(result.getReceiptUrl()).isEqualTo("https://receipt.url");
    }

    @Test
    @DisplayName("승인 응답의 orderId로 결제를 찾지 못하면 예외가 발생한다")
    void 승인_응답의_orderId로_결제를_찾지_못하면_예외가_발생한다() {
        ConfirmResponse response = new ConfirmResponse(
            "missing-order",
            "payment-key",
            "DONE",
            new BigDecimal("10000"),
            new ConfirmResponse.Receipt("https://receipt.url")
        );
        given(paymentJpaRepository.findByOrderId("missing-order")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.inprogressToDonePayment(response))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.NOT_FOUND_PAYMENT));
    }

    @Test
    @DisplayName("승인 응답 totalAmount가 null이면 ILLEGAL_AMOUNT 예외가 발생한다")
    void 승인_응답_totalAmount가_null이면_ILLEGAL_AMOUNT_예외가_발생한다() {
        Payment payment = payment("order-1", PaymentStatus.IN_PROGRESS, "10000");
        ConfirmResponse response = new ConfirmResponse(
            "order-1",
            "payment-key",
            "DONE",
            null,
            new ConfirmResponse.Receipt("https://receipt.url")
        );
        given(paymentJpaRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.inprogressToDonePayment(response))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.ILLEGAL_AMOUNT));
    }

    @Test
    @DisplayName("승인 응답 totalAmount가 결제 금액과 다르면 ILLEGAL_AMOUNT 예외가 발생한다")
    void 승인_응답_totalAmount가_결제_금액과_다르면_ILLEGAL_AMOUNT_예외가_발생한다() {
        Payment payment = payment("order-1", PaymentStatus.IN_PROGRESS, "10000");
        ConfirmResponse response = new ConfirmResponse(
            "order-1",
            "payment-key",
            "DONE",
            new BigDecimal("9000"),
            new ConfirmResponse.Receipt("https://receipt.url")
        );
        given(paymentJpaRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.inprogressToDonePayment(response))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.ILLEGAL_AMOUNT));
    }

    @Test
    @DisplayName("결제 상태가 IN_PROGRESS가 아니면 ALREADY_PROCESSED_PAYMENT 예외가 발생한다")
    void 결제_상태가_IN_PROGRESS가_아니면_ALREADY_PROCESSED_PAYMENT_예외가_발생한다() {
        Payment payment = payment("order-1", PaymentStatus.DONE, "10000");
        ConfirmResponse response = new ConfirmResponse(
            "order-1",
            "payment-key",
            "DONE",
            new BigDecimal("10000"),
            new ConfirmResponse.Receipt("https://receipt.url")
        );
        given(paymentJpaRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.inprogressToDonePayment(response))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT));
    }

    @Test
    @DisplayName("failConfirm은 IN_PROGRESS 결제 상태를 실패 상태로 변경한다")
    void failConfirm은_IN_PROGRESS_결제_상태를_실패_상태로_변경한다() {
        Payment payment = payment("order-1", PaymentStatus.IN_PROGRESS, "10000");
        PaymentConfirmReq request = confirmReq("order-1", "10000");
        given(paymentJpaRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        paymentService.failConfirm(request, PaymentStatus.ABORTED);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.ABORTED);
    }

    @Test
    @DisplayName("failConfirm에서 orderId로 결제를 찾지 못하면 예외가 발생한다")
    void failConfirm에서_orderId로_결제를_찾지_못하면_예외가_발생한다() {
        PaymentConfirmReq request = confirmReq("missing-order", "10000");
        given(paymentJpaRepository.findByOrderId("missing-order")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.failConfirm(request, PaymentStatus.ABORTED))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.NOT_FOUND_PAYMENT));
    }

    @Test
    @DisplayName("failConfirm에서 결제 상태가 IN_PROGRESS가 아니면 예외가 발생한다")
    void failConfirm에서_결제_상태가_IN_PROGRESS가_아니면_예외가_발생한다() {
        Payment payment = payment("order-1", PaymentStatus.DONE, "10000");
        PaymentConfirmReq request = confirmReq("order-1", "10000");
        given(paymentJpaRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.failConfirm(request, PaymentStatus.ABORTED))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT));
    }

    private PaymentConfirmReq confirmReq(String orderId, String approvedAmount) {
        return new PaymentConfirmReq(
            "CARD",
            orderId,
            "payment-key",
            new BigDecimal(approvedAmount),
            "TOSS"
        );
    }

    private Payment payment(String orderId, PaymentStatus status, String approvedAmount) {
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "orderId", orderId);
        ReflectionTestUtils.setField(payment, "paymentStatus", status);
        ReflectionTestUtils.setField(payment, "approvedAmount", new BigDecimal(approvedAmount));
        return payment;
    }
}
