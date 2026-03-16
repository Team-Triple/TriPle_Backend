package org.triple.backend.payment.unit.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.triple.backend.payment.config.TossPaymentProperties;
import org.triple.backend.payment.entity.outbox.Error;
import org.triple.backend.payment.entity.outbox.PaymentEventBody;
import org.triple.backend.payment.infra.TossPayment;
import org.triple.backend.payment.infra.dto.request.PaymentEventReq;
import org.triple.backend.payment.infra.dto.response.PaymentEventFailRes;
import org.triple.backend.payment.infra.dto.response.PaymentEventRes;
import org.triple.backend.payment.infra.dto.response.PaymentEventSuccessRes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TossPaymentTest {

    private static final String URI = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String SECRET = "test-secret";
    private static final String CONTENT_TYPE = "application/json";

    @Mock
    private RestClient tossPaymentClient;

    @Mock
    private RestClient.RequestBodyUriSpec postSpec;

    @Mock
    private RestClient.RequestBodySpec bodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private TossPayment tossPayment;

    @BeforeEach
    void setUp() {
        TossPaymentProperties properties = new TossPaymentProperties(URI, SECRET, CONTENT_TYPE, 3000L);
        tossPayment = new TossPayment(tossPaymentClient, properties);

        given(tossPaymentClient.post()).willReturn(postSpec);
        given(postSpec.uri(URI)).willReturn(bodySpec);
        given(bodySpec.header("Authorization", encodedSecretHeader())).willReturn(bodySpec);
        given(bodySpec.header("Content-Type", CONTENT_TYPE)).willReturn(bodySpec);
        given(bodySpec.body(any(PaymentEventReq.class))).willReturn(bodySpec);
        given(bodySpec.retrieve()).willReturn(responseSpec);
    }

    @Test
    @DisplayName("토스 승인 호출이 성공하면 성공 응답을 반환한다")
    void 토스_승인_호출이_성공하면_성공_응답을_반환한다() {
        PaymentEventSuccessRes expected = new PaymentEventSuccessRes(
                "order-1",
                "payment-key-1",
                "DONE",
                new BigDecimal("10000"),
                LocalDateTime.of(2030, 3, 20, 10, 0),
                new PaymentEventSuccessRes.Receipt("https://receipt")
        );
        given(responseSpec.body(PaymentEventSuccessRes.class)).willReturn(expected);

        PaymentEventRes result = tossPayment.request(sampleBody());

        assertThat(result).isEqualTo(expected);
        verify(tossPaymentClient).post();
    }

    @Test
    @DisplayName("네트워크 예외는 NETWORK_TIMEOUT으로 매핑한다")
    void 네트워크_예외는_NETWORK_TIMEOUT으로_매핑한다() {
        given(responseSpec.body(PaymentEventSuccessRes.class))
                .willThrow(new ResourceAccessException("timeout"));

        PaymentEventRes result = tossPayment.request(sampleBody());

        assertThat(result).isInstanceOf(PaymentEventFailRes.class);
        PaymentEventFailRes failRes = (PaymentEventFailRes) result;
        assertThat(failRes.orderId()).isEqualTo("order-1");
        assertThat(failRes.error()).isEqualTo(Error.NETWORK_TIMEOUT);
    }

    @Test
    @DisplayName("429 응답은 UPSTREAM_429로 매핑한다")
    void 응답코드_429는_UPSTREAM_429로_매핑한다() {
        given(responseSpec.body(PaymentEventSuccessRes.class))
                .willThrow(responseException(HttpStatus.TOO_MANY_REQUESTS));

        PaymentEventRes result = tossPayment.request(sampleBody());

        assertThat(result).isInstanceOf(PaymentEventFailRes.class);
        PaymentEventFailRes failRes = (PaymentEventFailRes) result;
        assertThat(failRes.error()).isEqualTo(Error.UPSTREAM_429);
    }

    @Test
    @DisplayName("5xx 응답은 UPSTREAM_5XX로 매핑한다")
    void 응답코드_5xx는_UPSTREAM_5XX로_매핑한다() {
        given(responseSpec.body(PaymentEventSuccessRes.class))
                .willThrow(responseException(HttpStatus.INTERNAL_SERVER_ERROR));

        PaymentEventRes result = tossPayment.request(sampleBody());

        assertThat(result).isInstanceOf(PaymentEventFailRes.class);
        PaymentEventFailRes failRes = (PaymentEventFailRes) result;
        assertThat(failRes.error()).isEqualTo(Error.UPSTREAM_5XX);
    }

    @Test
    @DisplayName("4xx 응답은 UPSTREAM_4XX로 매핑한다")
    void 응답코드_4xx는_UPSTREAM_4XX로_매핑한다() {
        given(responseSpec.body(PaymentEventSuccessRes.class))
                .willThrow(responseException(HttpStatus.BAD_REQUEST));

        PaymentEventRes result = tossPayment.request(sampleBody());

        assertThat(result).isInstanceOf(PaymentEventFailRes.class);
        PaymentEventFailRes failRes = (PaymentEventFailRes) result;
        assertThat(failRes.error()).isEqualTo(Error.UPSTREAM_4XX);
    }

    @Test
    @DisplayName("알 수 없는 런타임 예외는 UNKNOWN으로 매핑한다")
    void 알수없는_런타임_예외는_UNKNOWN으로_매핑한다() {
        given(responseSpec.body(PaymentEventSuccessRes.class))
                .willThrow(new IllegalStateException("boom"));

        PaymentEventRes result = tossPayment.request(sampleBody());

        assertThat(result).isInstanceOf(PaymentEventFailRes.class);
        PaymentEventFailRes failRes = (PaymentEventFailRes) result;
        assertThat(failRes.error()).isEqualTo(Error.UNKNOWN);
    }

    private RestClientResponseException responseException(HttpStatus httpStatus) {
        return new RestClientResponseException(
                "toss error",
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                HttpHeaders.EMPTY,
                "{}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
    }

    private PaymentEventBody sampleBody() {
        return PaymentEventBody.builder()
                .paymentKey("payment-key-1")
                .orderId("order-1")
                .requestedAmount(new BigDecimal("10000"))
                .build();
    }

    private String encodedSecretHeader() {
        return "Basic " + Base64.getEncoder().encodeToString((SECRET + ":").getBytes(StandardCharsets.UTF_8));
    }
}
