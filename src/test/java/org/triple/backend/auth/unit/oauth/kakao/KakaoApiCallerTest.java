package org.triple.backend.auth.unit.oauth.kakao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.triple.backend.auth.dto.response.KakaoTokenResponseDto;
import org.triple.backend.auth.exception.OauthTransientException;
import org.triple.backend.auth.oauth.kakao.KakaoApiCaller;
import org.triple.backend.auth.oauth.kakao.KakaoOauthProperties;
import org.triple.backend.global.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.web.client.RestClient.*;

@SpringBootTest(classes = { KakaoApiCaller.class, KakaoApiCallerTest.RetryTestConfig.class})
@ActiveProfiles("test")
class KakaoApiCallerTest {

    @Autowired
    private KakaoApiCaller kakaoApiCaller;

    @MockitoBean
    private RestClient restClient;

    @MockitoBean
    private RequestBodyUriSpec postSpec;

    @MockitoBean
    private RequestBodySpec bodySpec;

    @MockitoBean
    private ResponseSpec responseSpec;

    @TestConfiguration
    @EnableRetry
    static class RetryTestConfig {

        @Bean
        KakaoOauthProperties kakaoOauthProperties() {
            return new KakaoOauthProperties(
                    "https://kauth.kakao.com/oauth/token",
                    "https://kapi.kakao.com/v2/user/me",
                    "client-id",
                    "client-secret",
                    "http://localhost:3000/redirect/kakao"
            );
        }
    }


    @Test
    @DisplayName("연속 3번 오류 발생시 최종적으로 BusinessException 예외가 발생한다.")
    void 연속_3번_오류_발생시_최종적으로_BusinessException_에외가_발생한다() {
        // given
        given(restClient.post()).willReturn(postSpec);
        given(postSpec.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.contentType(any())).willReturn(bodySpec);
        given(bodySpec.accept(any())).willReturn(bodySpec);
        given(bodySpec.body(any())).willReturn(bodySpec);
        given(bodySpec.retrieve()).willReturn(responseSpec);

        given(responseSpec.body(KakaoTokenResponseDto.class))
                .willThrow(new OauthTransientException("Kakao token API 5xx", null));

        // when & then
        assertThatThrownBy(() -> kakaoApiCaller.requestAccessToken("code"))
                .isInstanceOf(BusinessException.class);

        then(restClient).should(times(3)).post();
    }
}