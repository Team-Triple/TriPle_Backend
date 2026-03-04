package org.triple.backend.payment.unit.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.triple.backend.payment.config.TossPaymentProperties;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.infra.TossPayment;
import org.triple.backend.payment.infra.dto.request.ConfirmRequest;
import org.triple.backend.payment.infra.dto.response.ConfirmFailResponse;
import org.triple.backend.payment.infra.dto.response.ConfirmResponse;
import org.triple.backend.payment.infra.exception.ConfirmAnonymousException;
import org.triple.backend.payment.infra.exception.ConfirmRecoverFailedException;
import org.triple.backend.payment.infra.exception.ConfirmServerException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@SpringBootTest(classes = {TossPayment.class, TossPaymentTest.RetryTestConfig.class})
@ActiveProfiles("test")
class TossPaymentTest {

    private static final String URI = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String SECRET = "test-secret";
    private static final String CONTENT_TYPE = "application/json";

    @Autowired
    private TossPayment tossPayment;

    @MockitoBean
    private RestClient tossPaymentClient;

    @MockitoBean
    private RestClient.RequestBodyUriSpec postSpec;

    @MockitoBean
    private RestClient.RequestBodySpec bodySpec;

    @MockitoBean
    private RestClient.ResponseSpec responseSpec;

    @TestConfiguration
    @EnableRetry
    static class RetryTestConfig {

        @Bean
        TossPaymentProperties tossPaymentProperties() {
            return new TossPaymentProperties(URI, SECRET, CONTENT_TYPE);
        }
    }

    @BeforeEach
    void setUp() {
        given(tossPaymentClient.post()).willReturn(postSpec);
        given(postSpec.uri(URI)).willReturn(bodySpec);
        given(bodySpec.header("Authorization", SECRET)).willReturn(bodySpec);
        given(bodySpec.header("Content-Type", CONTENT_TYPE)).willReturn(bodySpec);
        given(bodySpec.body(any(ConfirmRequest.class))).willReturn(bodySpec);
        given(bodySpec.retrieve()).willReturn(responseSpec);
    }

    @Test
    @DisplayName("confirm request succeeds and returns confirm response")
    void confirmSuccess() {
        ConfirmResponse expected = new ConfirmResponse(
                "order-1",
                "payment-1",
                "DONE",
                new BigDecimal("10000"),
                new ConfirmResponse.Receipt("https://receipt")
        );
        given(responseSpec.body(ConfirmResponse.class)).willReturn(expected);

        ConfirmResponse result = tossPayment.confirmRequest(samplePayment());

        assertThat(result).isEqualTo(expected);
        then(tossPaymentClient).should(times(1)).post();
    }

    @Test
    @DisplayName("429 response is retried and eventually fails with recover exception")
    void retryWhen429() {
        RestClientResponseException exception = responseException(
                HttpStatus.TOO_MANY_REQUESTS,
                new ConfirmFailResponse("PROVIDER_ERROR", "temporary error")
        );
        given(responseSpec.body(ConfirmResponse.class)).willThrow(exception);

        assertThatThrownBy(() -> tossPayment.confirmRequest(samplePayment()))
                .isInstanceOf(ConfirmRecoverFailedException.class);
        then(tossPaymentClient).should(times(3)).post();
    }

    @Test
    @DisplayName("400 response throws confirm server exception without retry")
    void throwConfirmServerExceptionWhen400() {
        RestClientResponseException exception = responseException(
                HttpStatus.BAD_REQUEST,
                new ConfirmFailResponse("INVALID_REQUEST", "bad request")
        );
        given(responseSpec.body(ConfirmResponse.class)).willThrow(exception);

        assertThatThrownBy(() -> tossPayment.confirmRequest(samplePayment()))
                .isInstanceOf(ConfirmServerException.class);
        then(tossPaymentClient).should(times(1)).post();
    }

    @Test
    @DisplayName("runtime exception is wrapped as confirm anonymous exception")
    void throwConfirmAnonymousExceptionWhenRuntimeException() {
        given(responseSpec.body(ConfirmResponse.class)).willThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> tossPayment.confirmRequest(samplePayment()))
                .isInstanceOf(ConfirmAnonymousException.class);
        then(tossPaymentClient).should(times(1)).post();
    }

    @Test
    @DisplayName("resource access exception is retried and eventually fails with recover exception")
    void retryWhenResourceAccessException() {
        given(responseSpec.body(ConfirmResponse.class)).willThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> tossPayment.confirmRequest(samplePayment()))
                .isInstanceOf(ConfirmRecoverFailedException.class);
        then(tossPaymentClient).should(times(3)).post();
    }

    private RestClientResponseException responseException(
            HttpStatus status,
            ConfirmFailResponse body
    ) {
        RestClientResponseException exception = new RestClientResponseException(
                "toss error",
                status,
                status.getReasonPhrase(),
                HttpHeaders.EMPTY,
                "{}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        exception.setBodyConvertFunction(resolvableType -> {
            if (ConfirmFailResponse.class.equals(resolvableType.toClass())) {
                return body;
            }
            return null;
        });
        return exception;
    }

    private Payment samplePayment() {
        return Payment.builder()
                .paymentKey("payment-1")
                .orderId("order-1")
                .approvedAmount(new BigDecimal("10000"))
                .build();
    }
}
