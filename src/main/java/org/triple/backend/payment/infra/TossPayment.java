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
import org.triple.backend.payment.infra.dto.request.ConfirmRequest;
import org.triple.backend.payment.infra.dto.response.ConfirmFailResponse;
import org.triple.backend.payment.infra.dto.response.ConfirmResponse;
import org.triple.backend.payment.infra.exception.ConfirmAnonymousException;
import org.triple.backend.payment.infra.exception.ConfirmRecoverFailedException;
import org.triple.backend.payment.infra.exception.ConfirmServerException;

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
            noRetryFor = {BusinessException.class, ConfirmServerException.class, ConfirmAnonymousException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    public ConfirmResponse confirmRequest(Payment payment) {
        try{
            return exchangeRequestToResponse(payment);
        } catch (ResourceAccessException e) {
            log.error("?ㅽ듃?뚰겕 ?덉쇅媛 諛쒖깮?덉뒿?덈떎.", e);
            throw e;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            ConfirmFailResponse confirmFailResponse = e.getResponseBodyAs(ConfirmFailResponse.class);

            if(status == 429 || status >= 500){ //?ъ떆??
                throw new ResourceAccessException("?ㅽ듃?뚰겕 ?덉쇅媛 諛쒖깮?덉뒿?덈떎.");
            }

            if(status == 400 && confirmFailResponse.message().equals("ALREADY_PROCESSED_PAYMENT")) {
                //?대? 泥섎━??寃곗젣????ConfirmRespnose瑜?諛섑솚?댁빞??=> 議고쉶 ?꾩슂
            }

            throw new ConfirmServerException("寃곗젣 ?뱀씤 ?쒕쾭?먯꽌 ?덉쇅媛 諛쒖깮?덉뒿?덈떎.", e);
        } catch (RuntimeException e) {
            throw new ConfirmAnonymousException("?????녿뒗 ?덉쇅媛 諛쒖깮?덉뒿?덈떎.", e);
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
    private ConfirmResponse recoverConfirmRequest(ResourceAccessException e, Payment payment) {
        log.error("?ъ떆??3???ㅽ뙣 ?ㅽ듃?뚰겕 ?덉쇅 諛쒖깮");
        throw new ConfirmRecoverFailedException("?ъ떆??3???ㅽ뙣 ?ㅽ듃?뚰겕 ?덉쇅 諛쒖깮", e);
    }

    @Recover
    private ConfirmResponse recoverConfirmServerException(ConfirmServerException e, Payment payment) {
        throw e;
    }

    @Recover
    private ConfirmResponse recoverConfirmAnonymousException(ConfirmAnonymousException e, Payment payment) {
        throw e;
    }
}





