package org.triple.backend.payment.unit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.infra.TossPayment;
import org.triple.backend.payment.infra.dto.response.ConfirmResponse;
import org.triple.backend.payment.repository.PaymentJpaRepository;
import org.triple.backend.payment.service.PaymentService;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceConfirmFlowTest {

    @Mock
    private TossPayment tossPayment;

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

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
                tossPayment,
                paymentJpaRepository,
                invoiceUserJpaRepository,
                invoiceJpaRepository,
                userTravelItineraryJpaRepository
        );
    }

    @Test
    @DisplayName("orderId로 결제를 찾지 못하면 NOT_FOUND_PAYMENT 예외가 발생한다.")
    void readyToInProgressNotFoundPayment() {
        PaymentConfirmReq req = confirmReq("order-1", "10000");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.readyToInProgressPayment(req, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.NOT_FOUND_PAYMENT));
    }

    @Test
    @DisplayName("READY 상태가 아닌 결제는 ALREADY_PROCESSED_PAYMENT 예외가 발생한다.")
    void readyToInProgressAlreadyProcessed() {
        Payment payment = payment("order-1", "10000", PaymentStatus.DONE);
        PaymentConfirmReq req = confirmReq("order-1", "10000");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.readyToInProgressPayment(req, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT));
    }

    @Test
    @DisplayName("요청 금액이 결제 금액과 다르면 ILLEGAL_AMOUNT 예외가 발생한다.")
    void readyToInProgressIllegalAmount() {
        Payment payment = payment("order-1", "10000", PaymentStatus.READY);
        PaymentConfirmReq req = confirmReq("order-1", "9000");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.readyToInProgressPayment(req, 1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.ILLEGAL_AMOUNT));
    }

    @Test
    @DisplayName("READY 결제는 IN_PROGRESS 상태로 변경된다.")
    void readyToInProgressSuccess() {
        Payment payment = payment("order-1", "10000", PaymentStatus.READY);
        PaymentConfirmReq req = confirmReq("order-1", "10000");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        Payment result = paymentService.readyToInProgressPayment(req, 1L, 1L);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("confirm은 tossPayment.confirmRequest 결과를 그대로 반환한다.")
    void confirmDelegatesToTossPayment() {
        Payment payment = payment("order-1", "10000", PaymentStatus.IN_PROGRESS);
        ConfirmResponse expected = new ConfirmResponse(
                "order-1",
                "payment-key-1",
                "DONE",
                new BigDecimal("10000"),
                new ConfirmResponse.Receipt("https://receipt")
        );
        given(tossPayment.confirmRequest(payment)).willReturn(expected);

        ConfirmResponse result = paymentService.confirm(payment);

        assertThat(result).isEqualTo(expected);
        verify(tossPayment).confirmRequest(payment);
    }

    @Test
    @DisplayName("승인 응답 orderId로 결제를 찾지 못하면 NOT_FOUND_PAYMENT 예외가 발생한다.")
    void inprogressToDoneNotFoundPayment() {
        ConfirmResponse response = confirmResponse("order-1", "10000");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.inprogressToDonePayment(response))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.NOT_FOUND_PAYMENT));
    }

    @Test
    @DisplayName("승인 응답 금액이 다르면 ILLEGAL_AMOUNT 예외가 발생한다.")
    void inprogressToDoneIllegalAmount() {
        Payment payment = payment("order-1", "10000", PaymentStatus.IN_PROGRESS);
        ConfirmResponse response = confirmResponse("order-1", "9000");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.inprogressToDonePayment(response))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.ILLEGAL_AMOUNT));
    }

    @Test
    @DisplayName("IN_PROGRESS 상태가 아니면 ALREADY_PROCESSED_PAYMENT 예외가 발생한다.")
    void inprogressToDoneAlreadyProcessed() {
        Payment payment = payment("order-1", "10000", PaymentStatus.DONE);
        ConfirmResponse response = confirmResponse("order-1", "10000");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.inprogressToDonePayment(response))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT));
    }

    @Test
    @DisplayName("정상 승인 응답이면 DONE 상태 결제 엔티티를 반환한다.")
    void inprogressToDoneSuccess() {
        Payment payment = payment("order-1", "10000", PaymentStatus.IN_PROGRESS);
        ConfirmResponse response = confirmResponse("order-1", "10000");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        Payment result = paymentService.inprogressToDonePayment(response);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(result.getPaymentKey()).isEqualTo("payment-key-1");
        assertThat(result.getApprovedAmount()).isEqualByComparingTo("10000");
        assertThat(result.getReceiptUrl()).contains("https://receipt");
    }

    @Test
    @DisplayName("실패 처리 시 결제를 찾지 못하면 NOT_FOUND_PAYMENT 예외가 발생한다.")
    void failConfirmNotFoundPayment() {
        PaymentConfirmReq req = confirmReq("order-1", "10000");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.failConfirm(req, PaymentStatus.FAILED))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.NOT_FOUND_PAYMENT));
    }

    @Test
    @DisplayName("IN_PROGRESS 상태가 아니면 실패 처리 시 ALREADY_PROCESSED_PAYMENT 예외가 발생한다.")
    void failConfirmAlreadyProcessed() {
        Payment payment = payment("order-1", "10000", PaymentStatus.DONE);
        PaymentConfirmReq req = confirmReq("order-1", "10000");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.failConfirm(req, PaymentStatus.FAILED))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT));
    }

    @Test
    @DisplayName("IN_PROGRESS 결제는 전달받은 실패 상태로 업데이트된다.")
    void failConfirmSuccess() {
        Payment payment = payment("order-1", "10000", PaymentStatus.IN_PROGRESS);
        PaymentConfirmReq req = confirmReq("order-1", "10000");
        given(paymentJpaRepository.findByOrderIdForUpdate("order-1")).willReturn(Optional.of(payment));

        paymentService.failConfirm(req, PaymentStatus.DB_FAILED);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.DB_FAILED);
    }

    private PaymentConfirmReq confirmReq(String orderId, String amount) {
        return new PaymentConfirmReq(
                "CARD",
                orderId,
                "payment-key-1",
                new BigDecimal(amount),
                "TOSS"
        );
    }

    private ConfirmResponse confirmResponse(String orderId, String amount) {
        return new ConfirmResponse(
                orderId,
                "payment-key-1",
                "DONE",
                new BigDecimal(amount),
                new ConfirmResponse.Receipt("https://receipt")
        );
    }

    private Payment payment(String orderId, String amount, PaymentStatus paymentStatus) {
        return Payment.builder()
                .orderId(orderId)
                .requestedAmount(new BigDecimal(amount))
                .approvedAmount(new BigDecimal(amount))
                .paymentStatus(paymentStatus)
                .build();
    }
}
