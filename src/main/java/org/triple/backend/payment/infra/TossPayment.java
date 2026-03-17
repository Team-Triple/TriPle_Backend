package org.triple.backend.payment.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.triple.backend.payment.config.TossPaymentProperties;
import org.triple.backend.payment.entity.outbox.Error;
import org.triple.backend.payment.entity.outbox.PaymentEventBody;
import org.triple.backend.payment.infra.dto.request.PaymentEventReq;
import org.triple.backend.payment.infra.dto.response.PaymentEventFailRes;
import org.triple.backend.payment.infra.dto.response.PaymentEventRes;
import org.triple.backend.payment.infra.dto.response.PaymentEventSuccessRes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPayment {
    private static final String AUTHORIZATION_HEADER_KEY = "Authorization";
    private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";

    private final RestClient tossPaymentClient;
    private final TossPaymentProperties tossPaymentProperties;

    public PaymentEventRes request(PaymentEventBody paymentEventBody) {
        try{
            return exchangeRequestToResponse(paymentEventBody);
        } catch (ResourceAccessException e) {
            return new PaymentEventFailRes(paymentEventBody.getOrderId(), Error.NETWORK_TIMEOUT, e.getMessage());
        } catch (RestClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            if(statusCode == 429) return new PaymentEventFailRes(paymentEventBody.getOrderId(), Error.UPSTREAM_429, e.getMessage());
            if(statusCode >= 500) return new PaymentEventFailRes(paymentEventBody.getOrderId(), Error.UPSTREAM_5XX, e.getMessage());
            return new PaymentEventFailRes(paymentEventBody.getOrderId(), Error.UPSTREAM_4XX, e.getMessage());
        } catch (RuntimeException e) {
            return new PaymentEventFailRes(paymentEventBody.getOrderId(), Error.UNKNOWN, e.getMessage());
        }
    }

    private PaymentEventRes exchangeRequestToResponse(PaymentEventBody paymentEventBody) {
        return tossPaymentClient.post()
                .uri(tossPaymentProperties.uri())
                .header(AUTHORIZATION_HEADER_KEY, encodeBase64(tossPaymentProperties.secret()))
                .header(CONTENT_TYPE_HEADER_KEY, tossPaymentProperties.contentType())
                .body(makeRequestBody(paymentEventBody))
                .retrieve()
                .body(PaymentEventSuccessRes.class);
    }

    private PaymentEventReq makeRequestBody(PaymentEventBody paymentEventBody) {
        return PaymentEventReq.from(paymentEventBody);
    }

    private String encodeBase64(String secretKey) {
        return "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }
}
