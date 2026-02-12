package org.triple.backend.auth.oauth.kakao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.triple.backend.auth.dto.response.KakaoUserInfoResponseDto;
import org.triple.backend.auth.oauth.OauthClient;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.oauth.OauthUser;

import static org.triple.backend.auth.dto.response.KakaoUserInfoResponseDto.KakaoAccount;
import static org.triple.backend.auth.dto.response.KakaoUserInfoResponseDto.KakaoProfile;

@Component
@RequiredArgsConstructor
public class KakaoOauthClient implements OauthClient {

    private final KakaoApiCaller kakaoApiCaller;

    @Override
    public OauthProvider provider() {
        return OauthProvider.KAKAO;
    }

    @Override
    public OauthUser fetchUser(final String code) {
        String accessToken = kakaoApiCaller.requestAccessToken(code);
        KakaoUserInfoResponseDto dto = kakaoApiCaller.requestUserInfo(accessToken);

        String providerId = dto.id();
        KakaoAccount account = dto.kakaoAccount();
        KakaoProfile profile = account != null ? account.profile() : null;

        String email = account != null ? account.email() : null;
        String nickname = profile != null ? profile.nickname() : null;
        String profileUrl = profile != null ? profile.profileImageUrl() : null;

        return new OauthUser(provider(), providerId, email, nickname, profileUrl);
    }
}
