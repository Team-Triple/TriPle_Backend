package org.triple.backend.auth.unit.oauth.kakao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.triple.backend.auth.dto.response.KakaoUserInfoResponseDto;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.oauth.OauthUser;
import org.triple.backend.auth.oauth.kakao.KakaoApiCaller;
import org.triple.backend.auth.oauth.kakao.KakaoOauthClient;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class KakaoOauthClientTest {

    @Mock
    private KakaoApiCaller kakaoApiCaller;

    @InjectMocks
    private KakaoOauthClient kakaoOauthClient;


    @Test
    @DisplayName("카카오 유저 조회 성공 시 OauthUser로 매핑한다")
    void 카카오_유저_조회_성공_시_OauthUser로_매핑한다() {
        // given
        String code = "auth-code";
        String accessToken = "access-token";

        KakaoUserInfoResponseDto.KakaoProfile profile =
                new KakaoUserInfoResponseDto.KakaoProfile("닉네임", "https://img.url/p.png");
        KakaoUserInfoResponseDto.KakaoAccount account =
                new KakaoUserInfoResponseDto.KakaoAccount(profile, "test@test.com");

        KakaoUserInfoResponseDto dto =
                new KakaoUserInfoResponseDto("kakao-1234", new Properties(), account);

        given(kakaoApiCaller.requestAccessToken(code)).willReturn(accessToken);
        given(kakaoApiCaller.requestUserInfo(accessToken)).willReturn(dto);

        // when
        OauthUser user = kakaoOauthClient.fetchUser(code);

        // then
        assertThat(user.provider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(user.providerId()).isEqualTo("kakao-1234");
        assertThat(user.email()).isEqualTo("test@test.com");
        assertThat(user.nickname()).isEqualTo("닉네임");
        assertThat(user.profileUrl()).isEqualTo("https://img.url/p.png");

        then(kakaoApiCaller).should().requestAccessToken(code);
        then(kakaoApiCaller).should().requestUserInfo(accessToken);
    }


    @Test
    @DisplayName("kakaoAccount가 null이면 email,nickname,profileUrl은 null로 매핑한다")
    void kakaoAccount가_null이면_email_nickname_profileUrl은_null로_매핑한다() {
        // given
        String code = "auth-code";
        String accessToken = "access-token";

        KakaoUserInfoResponseDto dto =
                new KakaoUserInfoResponseDto("kakao-1234", new Properties(), null);

        given(kakaoApiCaller.requestAccessToken(code)).willReturn(accessToken);
        given(kakaoApiCaller.requestUserInfo(accessToken)).willReturn(dto);

        // when
        OauthUser user = kakaoOauthClient.fetchUser(code);

        // then
        assertThat(user.provider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(user.providerId()).isEqualTo("kakao-1234");
        assertThat(user.email()).isNull();
        assertThat(user.nickname()).isNull();
        assertThat(user.profileUrl()).isNull();
    }

    @Test
    @DisplayName("profile이 null이면 nickname,profileUrl은 null로 매핑한다")
    void profile이_null이면_nickname_profileUrl은_null로_매핑한다() {
        // given
        String code = "auth-code";
        String accessToken = "access-token";

        KakaoUserInfoResponseDto.KakaoAccount account =
                new KakaoUserInfoResponseDto.KakaoAccount(null, "test@test.com");

        KakaoUserInfoResponseDto dto =
                new KakaoUserInfoResponseDto("kakao-1234", new Properties(), account);

        given(kakaoApiCaller.requestAccessToken(code)).willReturn(accessToken);
        given(kakaoApiCaller.requestUserInfo(accessToken)).willReturn(dto);

        // when
        OauthUser user = kakaoOauthClient.fetchUser(code);

        // then
        assertThat(user.provider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(user.providerId()).isEqualTo("kakao-1234");
        assertThat(user.email()).isEqualTo("test@test.com");
        assertThat(user.nickname()).isNull();
        assertThat(user.profileUrl()).isNull();
    }
}