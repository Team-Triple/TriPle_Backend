package org.triple.backend.payment.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.payment.config.TossPaymentProperties;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.infra.exception.ConfirmAnonymousException;
import org.triple.backend.payment.infra.exception.ConfirmRecoverFailedException;
import org.triple.backend.payment.infra.exception.ConfirmServerException;
import org.triple.backend.payment.infra.dto.ConfirmResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPayment {
    private static final String AUTHORIZATION_HEADER_KEY = "Authorization";
    private static final String CONTENT_TYPE_HEADER_KEY = "Content-Type";

    private final RestClient tossPaymentClient;
    private final TossPaymentProperties tossPaymentProperties;

    @Retryable(
        retryFor = {ResourceAccessException.class},
        noRetryFor = {BusinessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    public ConfirmResponse confirmRequest(Payment payment) {
        try{
            return exchangeRequestToResponse(payment);
        } catch (ResourceAccessException e) {
            log.error("네트워크 예외가 발생했습니다.", e);
            throw e;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            log.error("Toss API error. status={}, body={}", status, body, e);
            if(status == 429 || status >= 500){
                throw new ResourceAccessException("네트워크 예외가 발생했습니다.");
            }
            throw new ConfirmServerException("결제 승인 서버에서 예외가 발생했습니다.", e);
        } catch (RuntimeException e) {
            throw new ConfirmAnonymousException("알 수 없는 예외가 발생했습니다.", e);
        }
    }

    private ConfirmResponse exchangeRequestToResponse(Payment payment) {
        return tossPaymentClient.post()
            .uri(tossPaymentProperties.uri())
            .header(AUTHORIZATION_HEADER_KEY, tossPaymentProperties.secret())
            .header(CONTENT_TYPE_HEADER_KEY, tossPaymentProperties.contentType())
            .body(makeRequestBody(payment))
            .retrieve()
            .body(ConfirmResponse.class);
    }

    private ConfirmRequest makeRequestBody(Payment payment) {
        return ConfirmRequest.from(payment);
    }

    @Recover
    private void recoverConfirmRequest(ResourceAccessException e, Payment payment) {
        log.error("재시도 3회 실패 네트워크 예외 발생");
        throw new ConfirmRecoverFailedException("재시도 3회 실패 네트워크 예외 발생");
    }
}




