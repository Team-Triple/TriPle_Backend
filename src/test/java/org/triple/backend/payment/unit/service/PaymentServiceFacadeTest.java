package org.triple.backend.payment.unit.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.dto.response.PaymentConfirmRes;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.infra.exception.ConfirmAnonymousException;
import org.triple.backend.payment.infra.exception.ConfirmRecoverFailedException;
import org.triple.backend.payment.infra.exception.ConfirmServerException;
import org.triple.backend.payment.infra.dto.ConfirmResponse;
import org.triple.backend.payment.service.PaymentService;
import org.triple.backend.payment.service.PaymentServiceFacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceFacadeTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentServiceFacade paymentServiceFacade;

    @Test
    @DisplayName("결제 승인 전체 단계가 성공하면 응답을 반환한다")
    void 결제_승인_전체_단계가_성공하면_응답을_반환한다() {
        Long invoiceId = 1L;
        Long userId = 2L;
        PaymentConfirmReq request = confirmReq("order-1", "10000");
        Payment inProgressPayment = payment("order-1", PaymentStatus.IN_PROGRESS, "10000", null);
        ConfirmResponse confirmResponse = confirmResponse("order-1", "10000", "https://receipt.url");
        Payment donePayment = payment("order-1", PaymentStatus.DONE, "10000", "https://receipt.url");

        given(paymentService.readyToInProgressPayment(request, invoiceId, userId))
            .willReturn(inProgressPayment);
        given(paymentService.confirm(inProgressPayment)).willReturn(confirmResponse);
        given(paymentService.inprogressToDonePayment(confirmResponse)).willReturn(donePayment);

        PaymentConfirmRes result = paymentServiceFacade.confirm(request, invoiceId, userId);

        assertThat(result.orderId()).isEqualTo("order-1");
        assertThat(result.amount()).isEqualByComparingTo("10000");
        assertThat(result.receiptUrl()).isEqualTo("https://receipt.url");
        assertThat(result.status()).isEqualTo(PaymentStatus.DONE);

        InOrder inOrder = inOrder(paymentService);
        inOrder.verify(paymentService).readyToInProgressPayment(request, invoiceId, userId);
        inOrder.verify(paymentService).confirm(inProgressPayment);
        inOrder.verify(paymentService).inprogressToDonePayment(confirmResponse);
        verify(paymentService, never()).failConfirm(request, PaymentStatus.ABORTED);
        verify(paymentService, never()).failConfirm(request, PaymentStatus.EXPIRED);
    }

    @Test
    @DisplayName("재시도 소진 예외가 발생하면 CONFIRM_FAILED로 변환하고 EXPIRED 처리한다")
    void 재시도_소진_예외가_발생하면_CONFIRM_FAILED로_변환하고_EXPIRED_처리한다() {
        Long invoiceId = 1L;
        Long userId = 2L;
        PaymentConfirmReq request = confirmReq("order-1", "10000");
        Payment inProgressPayment = payment("order-1", PaymentStatus.IN_PROGRESS, "10000", null);

        given(paymentService.readyToInProgressPayment(request, invoiceId, userId))
            .willReturn(inProgressPayment);
        given(paymentService.confirm(inProgressPayment))
            .willThrow(new ConfirmRecoverFailedException("network retry failed"));

        assertThatThrownBy(() -> paymentServiceFacade.confirm(request, invoiceId, userId))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.CONFIRM_FAILED));

        verify(paymentService).failConfirm(request, PaymentStatus.EXPIRED);
    }

    @Test
    @DisplayName("알 수 없는 승인 예외가 발생하면 CONFIRM_FAILED로 변환하고 ABORTED 처리한다")
    void 알_수_없는_승인_예외가_발생하면_CONFIRM_FAILED로_변환하고_ABORTED_처리한다() {
        Long invoiceId = 1L;
        Long userId = 2L;
        PaymentConfirmReq request = confirmReq("order-1", "10000");
        Payment inProgressPayment = payment("order-1", PaymentStatus.IN_PROGRESS, "10000", null);

        given(paymentService.readyToInProgressPayment(request, invoiceId, userId))
            .willReturn(inProgressPayment);
        given(paymentService.confirm(inProgressPayment))
            .willThrow(new ConfirmAnonymousException("unknown"));

        assertThatThrownBy(() -> paymentServiceFacade.confirm(request, invoiceId, userId))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.CONFIRM_FAILED));

        verify(paymentService).failConfirm(request, PaymentStatus.ABORTED);
    }

    @Test
    @DisplayName("서버 예외가 발생하면 CONFIRM_FAILED로 변환하고 ABORTED 처리한다")
    void 서버_예외가_발생하면_CONFIRM_FAILED로_변환하고_ABORTED_처리한다() {
        Long invoiceId = 1L;
        Long userId = 2L;
        PaymentConfirmReq request = confirmReq("order-1", "10000");
        Payment inProgressPayment = payment("order-1", PaymentStatus.IN_PROGRESS, "10000", null);

        given(paymentService.readyToInProgressPayment(request, invoiceId, userId))
            .willReturn(inProgressPayment);
        given(paymentService.confirm(inProgressPayment))
            .willThrow(new ConfirmServerException("server down"));

        assertThatThrownBy(() -> paymentServiceFacade.confirm(request, invoiceId, userId))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.CONFIRM_FAILED));

        verify(paymentService).failConfirm(request, PaymentStatus.ABORTED);
    }

    @Test
    @DisplayName("DB 예외가 발생하면 CONFIRM_FAILED로 변환하고 ABORTED 처리한다")
    void DB_예외가_발생하면_CONFIRM_FAILED로_변환하고_ABORTED_처리한다() {
        Long invoiceId = 1L;
        Long userId = 2L;
        PaymentConfirmReq request = confirmReq("order-1", "10000");
        Payment inProgressPayment = payment("order-1", PaymentStatus.IN_PROGRESS, "10000", null);
        ConfirmResponse confirmResponse = confirmResponse("order-1", "10000", "https://receipt.url");

        given(paymentService.readyToInProgressPayment(request, invoiceId, userId))
            .willReturn(inProgressPayment);
        given(paymentService.confirm(inProgressPayment)).willReturn(confirmResponse);
        given(paymentService.inprogressToDonePayment(confirmResponse))
            .willThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> paymentServiceFacade.confirm(request, invoiceId, userId))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.CONFIRM_FAILED));

        verify(paymentService).failConfirm(request, PaymentStatus.ABORTED);
    }

    @Test
    @DisplayName("사전 검증 단계의 비즈니스 예외는 그대로 전파한다")
    void 사전_검증_단계의_비즈니스_예외는_그대로_전파한다() {
        Long invoiceId = 1L;
        Long userId = 2L;
        PaymentConfirmReq request = confirmReq("order-1", "10000");

        given(paymentService.readyToInProgressPayment(request, invoiceId, userId))
            .willThrow(new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT));

        assertThatThrownBy(() -> paymentServiceFacade.confirm(request, invoiceId, userId))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(PaymentErrorCode.NOT_FOUND_PAYMENT));

        verify(paymentService, never()).failConfirm(request, PaymentStatus.ABORTED);
        verify(paymentService, never()).failConfirm(request, PaymentStatus.EXPIRED);
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

    private ConfirmResponse confirmResponse(String orderId, String amount, String receiptUrl) {
        return new ConfirmResponse(
            orderId,
            "payment-key",
            "DONE",
            new BigDecimal(amount),
            new ConfirmResponse.Receipt(receiptUrl)
        );
    }

    private Payment payment(
        String orderId,
        PaymentStatus paymentStatus,
        String approvedAmount,
        String receiptUrl
    ) {
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "orderId", orderId);
        ReflectionTestUtils.setField(payment, "paymentStatus", paymentStatus);
        ReflectionTestUtils.setField(payment, "approvedAmount", new BigDecimal(approvedAmount));
        ReflectionTestUtils.setField(payment, "receiptUrl", receiptUrl);
        return payment;
    }
}
