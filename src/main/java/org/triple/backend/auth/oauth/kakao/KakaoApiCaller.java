package org.triple.backend.auth.oauth.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.triple.backend.auth.dto.response.KakaoTokenResponseDto;
import org.triple.backend.auth.dto.response.KakaoUserInfoResponseDto;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.exception.OauthTransientException;
import org.springframework.retry.annotation.Backoff;
import org.triple.backend.global.error.BusinessException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoApiCaller {

    private final KakaoOauthProperties props;
    private final RestClient restClient;

    @Retryable(
            retryFor = {OauthTransientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 300, multiplier = 2.0, maxDelay = 3000)
    )
    public String requestAccessToken(final String code) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", props.clientId());
            form.add("redirect_uri", props.redirectUri());
            form.add("code", code);
            form.add("client_secret", props.clientSecret());

            KakaoTokenResponseDto kakaoTokenResponseDto = restClient.post()
                    .uri(props.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BusinessException(AuthErrorCode.FAILED_ISSUE_KAKAO_ACCESS_TOKEN);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new OauthTransientException("Kakao token API 5xx", null);
                    })
                    .body(KakaoTokenResponseDto.class);

            if (kakaoTokenResponseDto == null || kakaoTokenResponseDto.accessToken() == null) {
                throw new BusinessException(AuthErrorCode.FAILED_ISSUE_KAKAO_ACCESS_TOKEN);
            }

            return kakaoTokenResponseDto.accessToken();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new OauthTransientException("Kakao token API call failed", e);
        }
    }

    @Retryable(
            retryFor = {OauthTransientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 300, multiplier = 2.0, maxDelay = 3000)
    )
    public KakaoUserInfoResponseDto requestUserInfo(final String accessToken) {
        try {
            KakaoUserInfoResponseDto kakaoUserInfoResponseDto = restClient.get()
                    .uri(props.userInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BusinessException(AuthErrorCode.FAILED_FIND_KAKAO_USER_INFO);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new OauthTransientException("Kakao userinfo API 5xx", null);
                    })
                    .body(KakaoUserInfoResponseDto.class);

            if (kakaoUserInfoResponseDto == null || kakaoUserInfoResponseDto.id() == null) {
                throw new BusinessException(AuthErrorCode.FAILED_FIND_KAKAO_USER_INFO);
            }
            return kakaoUserInfoResponseDto;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new OauthTransientException("Kakao userInfo API 호출 실패", e);
        }
    }

    @Recover
    public String recoverAccessToken(OauthTransientException e, String code) {
        log.error(
                "Kakao OAuth access token 요청 재시도 실패, provider={}, tokenUri={}, causeType={}, message={}",
                "KAKAO",
                props.tokenUri(),
                (e.getCause() != null ? e.getCause().getClass().getSimpleName() : e.getClass().getSimpleName()),
                e.getMessage(),
                e
        );
        throw new BusinessException(AuthErrorCode.FAILED_ISSUE_KAKAO_ACCESS_TOKEN);
    }

    @Recover
    public KakaoUserInfoResponseDto recoverUserInfo(OauthTransientException e, String accessToken) {
        log.error(
                "Kakao OAuth 유저 정보요청 재시도 실패, provider={}, userInfoUri={}, causeType={}, message={}",
                "KAKAO",
                props.userInfoUri(),
                (e.getCause() != null ? e.getCause().getClass().getSimpleName() : e.getClass().getSimpleName()),
                e.getMessage(),
                e
        );
        throw new BusinessException(AuthErrorCode.FAILED_FIND_KAKAO_USER_INFO);
    }
}
