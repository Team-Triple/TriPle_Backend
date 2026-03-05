package org.triple.backend.payment.unit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.dto.response.PaymentConfirmRes;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.infra.dto.response.ConfirmResponse;
import org.triple.backend.payment.infra.exception.ConfirmAnonymousException;
import org.triple.backend.payment.infra.exception.ConfirmRecoverFailedException;
import org.triple.backend.payment.infra.exception.ConfirmServerException;
import org.triple.backend.payment.service.PaymentService;
import org.triple.backend.payment.service.PaymentServiceFacade;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceFacadeTest {

    @Mock
    private PaymentService paymentService;

    private PaymentServiceFacade paymentServiceFacade;

    @BeforeEach
    void setUp() {
        paymentServiceFacade = new PaymentServiceFacade(paymentService);
    }

    @Test
    @DisplayName("결제 승인이 성공하면 PaymentConfirmRes를 반환한다.")
    void confirmSuccess() {
        PaymentConfirmReq req = confirmReq();
        Payment inProgress = payment("order-1", "10000", PaymentStatus.IN_PROGRESS);
        ConfirmResponse confirmResponse = new ConfirmResponse(
                "order-1",
                "payment-key-1",
                "DONE",
                new BigDecimal("10000"),
                new ConfirmResponse.Receipt("https://receipt")
        );
        Payment donePayment = payment("order-1", "10000", PaymentStatus.DONE);

        given(paymentService.readyToInProgressPayment(req, 1L, 1L)).willReturn(inProgress);
        given(paymentService.confirm(inProgress)).willReturn(confirmResponse);
        given(paymentService.inprogressToDonePayment(confirmResponse)).willReturn(donePayment);

        PaymentConfirmRes result = paymentServiceFacade.confirm(req, 1L, 1L);

        assertThat(result.orderId()).isEqualTo("order-1");
        assertThat(result.amount()).isEqualByComparingTo("10000");
        assertThat(result.receiptUrl()).isEqualTo("https://receipt");
        assertThat(result.status()).isEqualTo(PaymentStatus.DONE);
    }

    @Test
    @DisplayName("재시도 최종 실패 시 RETRY_FAILED로 상태를 바꾸고 CONFIRM_FAILED 예외를 던진다.")
    void confirmRecoverFailedException() {
        PaymentConfirmReq req = confirmReq();
        Payment inProgress = payment("order-1", "10000", PaymentStatus.IN_PROGRESS);
        given(paymentService.readyToInProgressPayment(req, 1L, 1L)).willReturn(inProgress);
        given(paymentService.confirm(inProgress)).willThrow(new ConfirmRecoverFailedException("retry failed"));

        assertThatThrownBy(() -> paymentServiceFacade.confirm(req, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.CONFIRM_FAILED));

        verify(paymentService).failConfirm(req, PaymentStatus.RETRY_FAILED);
    }

    @Test
    @DisplayName("알 수 없는 승인 실패 시 FAILED로 상태를 바꾸고 CONFIRM_FAILED 예외를 던진다.")
    void confirmAnonymousException() {
        PaymentConfirmReq req = confirmReq();
        Payment inProgress = payment("order-1", "10000", PaymentStatus.IN_PROGRESS);
        given(paymentService.readyToInProgressPayment(req, 1L, 1L)).willReturn(inProgress);
        given(paymentService.confirm(inProgress)).willThrow(new ConfirmAnonymousException("unknown"));

        assertThatThrownBy(() -> paymentServiceFacade.confirm(req, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.CONFIRM_FAILED));

        verify(paymentService).failConfirm(req, PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("서버 승인 실패 시 FAILED로 상태를 바꾸고 CONFIRM_FAILED 예외를 던진다.")
    void confirmServerException() {
        PaymentConfirmReq req = confirmReq();
        Payment inProgress = payment("order-1", "10000", PaymentStatus.IN_PROGRESS);
        given(paymentService.readyToInProgressPayment(req, 1L, 1L)).willReturn(inProgress);
        given(paymentService.confirm(inProgress)).willThrow(new ConfirmServerException("server failed"));

        assertThatThrownBy(() -> paymentServiceFacade.confirm(req, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.CONFIRM_FAILED));

        verify(paymentService).failConfirm(req, PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("DB 반영 실패 시 DB_FAILED로 상태를 바꾸고 CONFIRM_FAILED 예외를 던진다.")
    void confirmDataAccessException() {
        PaymentConfirmReq req = confirmReq();
        Payment inProgress = payment("order-1", "10000", PaymentStatus.IN_PROGRESS);
        ConfirmResponse confirmResponse = new ConfirmResponse(
                "order-1",
                "payment-key-1",
                "DONE",
                new BigDecimal("10000"),
                new ConfirmResponse.Receipt("https://receipt")
        );
        given(paymentService.readyToInProgressPayment(req, 1L, 1L)).willReturn(inProgress);
        given(paymentService.confirm(inProgress)).willReturn(confirmResponse);
        given(paymentService.inprogressToDonePayment(confirmResponse))
                .willThrow(new DataRetrievalFailureException("db failed"));

        assertThatThrownBy(() -> paymentServiceFacade.confirm(req, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.CONFIRM_FAILED));

        verify(paymentService).failConfirm(req, PaymentStatus.DB_FAILED);
    }

    private PaymentConfirmReq confirmReq() {
        return new PaymentConfirmReq(
                "CARD",
                "order-1",
                "payment-key-1",
                new BigDecimal("10000"),
                "TOSS"
        );
    }

    private Payment payment(String orderId, String amount, PaymentStatus status) {
        return Payment.builder()
                .orderId(orderId)
                .approvedAmount(new BigDecimal(amount))
                .receiptUrl("https://receipt")
                .paymentStatus(status)
                .build();
    }
}
