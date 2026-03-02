package org.triple.backend.payment.unit.infra;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.triple.backend.payment.config.TossPaymentProperties;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.infra.TossPayment;
import org.triple.backend.payment.infra.dto.ConfirmRequest;
import org.triple.backend.payment.infra.dto.ConfirmResponse;
import org.triple.backend.payment.infra.exception.ConfirmAnonymousException;
import org.triple.backend.payment.infra.exception.ConfirmRecoverFailedException;
import org.triple.backend.payment.infra.exception.ConfirmServerException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.web.client.RestClient.RequestBodySpec;
import static org.springframework.web.client.RestClient.RequestBodyUriSpec;
import static org.springframework.web.client.RestClient.ResponseSpec;

@ExtendWith(MockitoExtension.class)
class TossPaymentTest {

    @Mock
    private RestClient tossPaymentClient;

    @Mock
    private RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RequestBodySpec requestBodySpec;

    @Mock
    private ResponseSpec responseSpec;

    private TossPayment tossPayment;
    private TossPaymentProperties tossPaymentProperties;

    @BeforeEach
    void setUp() {
        tossPaymentProperties = new TossPaymentProperties(
            "https://api.tosspayments.com/v1/payments/confirm",
            "Basic test-secret",
            "application/json"
        );
        tossPayment = new TossPayment(tossPaymentClient, tossPaymentProperties);
    }

    @Test
    @DisplayName("결제 승인 요청이 성공하면 응답을 반환한다")
    void 결제_승인_요청이_성공하면_응답을_반환한다() {
        Payment payment = 결제_생성();
        ConfirmResponse expected = new ConfirmResponse(
            "order-1",
            "payment-key",
            "DONE",
            new BigDecimal("10000"),
            new ConfirmResponse.Receipt("https://receipt.url")
        );

        요청_체인_목설정();
        given(responseSpec.body(ConfirmResponse.class)).willReturn(expected);

        ConfirmResponse result = tossPayment.confirmRequest(payment);

        assertThat(result).isSameAs(expected);

        verify(requestBodyUriSpec).uri(tossPaymentProperties.uri());
        verify(requestBodySpec).header("Authorization", tossPaymentProperties.secret());
        verify(requestBodySpec).header("Content-Type", tossPaymentProperties.contentType());

        ArgumentCaptor<ConfirmRequest> captor = ArgumentCaptor.forClass(ConfirmRequest.class);
        verify(requestBodySpec).body(captor.capture());
        ConfirmRequest captured = captor.getValue();
        assertThat(captured.paymentKey()).isEqualTo("payment-key");
        assertThat(captured.orderId()).isEqualTo("order-1");
        assertThat(captured.amount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("네트워크 예외가 발생하면 그대로 전파한다")
    void 네트워크_예외가_발생하면_그대로_전파한다() {
        Payment payment = 결제_생성();
        ResourceAccessException networkException = new ResourceAccessException("network fail");

        요청_체인_목설정();
        given(responseSpec.body(ConfirmResponse.class)).willThrow(networkException);

        assertThatThrownBy(() -> tossPayment.confirmRequest(payment))
            .isSameAs(networkException);
    }

    @Test
    @DisplayName("429 응답이면 네트워크 예외로 변환한다")
    void 사백이십구_응답이면_네트워크_예외로_변환한다() {
        Payment payment = 결제_생성();
        HttpClientErrorException tooManyRequests = HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too Many Requests",
            HttpHeaders.EMPTY,
            "{\"code\":\"TOO_MANY_REQUESTS\"}".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );

        요청_체인_목설정();
        given(responseSpec.body(ConfirmResponse.class)).willThrow(tooManyRequests);

        assertThatThrownBy(() -> tossPayment.confirmRequest(payment))
            .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("5xx 응답이면 네트워크 예외로 변환한다")
    void 오xx_응답이면_네트워크_예외로_변환한다() {
        Payment payment = 결제_생성();
        HttpServerErrorException serverError = HttpServerErrorException.create(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            HttpHeaders.EMPTY,
            "{\"code\":\"INTERNAL_SERVER_ERROR\"}".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );

        요청_체인_목설정();
        given(responseSpec.body(ConfirmResponse.class)).willThrow(serverError);

        assertThatThrownBy(() -> tossPayment.confirmRequest(payment))
            .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("4xx 응답이면 ConfirmServerException으로 변환한다")
    void 사xx_응답이면_ConfirmServerException으로_변환한다() {
        Payment payment = 결제_생성();
        HttpClientErrorException badRequest = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            HttpHeaders.EMPTY,
            "{\"code\":\"BAD_REQUEST\"}".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );

        요청_체인_목설정();
        given(responseSpec.body(ConfirmResponse.class)).willThrow(badRequest);

        assertThatThrownBy(() -> tossPayment.confirmRequest(payment))
            .isInstanceOf(ConfirmServerException.class)
            .hasCause(badRequest);
    }

    @Test
    @DisplayName("알 수 없는 런타임 예외면 ConfirmAnonymousException으로 변환한다")
    void 알_수_없는_런타임_예외면_ConfirmAnonymousException으로_변환한다() {
        Payment payment = 결제_생성();
        RuntimeException unknown = new RuntimeException("unknown");

        요청_체인_목설정();
        given(responseSpec.body(ConfirmResponse.class)).willThrow(unknown);

        assertThatThrownBy(() -> tossPayment.confirmRequest(payment))
            .isInstanceOf(ConfirmAnonymousException.class)
            .hasCause(unknown);
    }

    @Test
    @DisplayName("재시도 최종 실패 복구 메서드는 ConfirmRecoverFailedException을 던진다")
    void 재시도_최종_실패_복구_메서드는_ConfirmRecoverFailedException을_던진다() {
        Payment payment = 결제_생성();
        ResourceAccessException networkException = new ResourceAccessException("network fail");

        assertThatThrownBy(() ->
            ReflectionTestUtils.invokeMethod(
                tossPayment,
                "recoverConfirmRequest",
                networkException,
                payment
            )
        ).isInstanceOf(ConfirmRecoverFailedException.class);
    }

    private void 요청_체인_목설정() {
        given(tossPaymentClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(tossPaymentProperties.uri())).willReturn(requestBodySpec);
        given(requestBodySpec.header("Authorization", tossPaymentProperties.secret())).willReturn(requestBodySpec);
        given(requestBodySpec.header("Content-Type", tossPaymentProperties.contentType())).willReturn(requestBodySpec);
        given(requestBodySpec.body(any(ConfirmRequest.class))).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
    }

    private Payment 결제_생성() {
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "orderId", "order-1");
        ReflectionTestUtils.setField(payment, "paymentKey", "payment-key");
        ReflectionTestUtils.setField(payment, "approvedAmount", new BigDecimal("10000"));
        return payment;
    }
}
