package org.triple.backend.auth.unit.service;

import jakarta.servlet.http.HttpSession;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.triple.backend.auth.dto.request.AuthLoginRequestDto;
import org.triple.backend.auth.dto.response.AuthLoginResponseDto;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.oauth.OauthClient;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.auth.oauth.OauthUser;
import org.triple.backend.auth.service.AuthService;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;


@Import({AuthService.class, SessionManager.class})
@ServiceTest
public class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @MockitoBean
    private Map<OauthProvider, OauthClient> clients;

    @MockitoBean
    OauthClient kakaoClient;

    @BeforeEach
    void setUp() {
        userJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("지원하지 않는 provider면 UNSUPPORTED_OAUTH_PROVIDER 예외")
    void 지원하지_않는_provider면_UNSUPPORTED_OAUTH_PROVIDER_에외() {
        // given
        AuthLoginRequestDto req = new AuthLoginRequestDto("code", OauthProvider.KAKAO);
        given(clients.get(OauthProvider.KAKAO)).willReturn(null);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        // when & then
        assertThatThrownBy(() -> authService.login(req, servletRequest))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    }

    @Test
    @DisplayName("미가입 유저면 회원가입 후 userId 반환과 세션에 USER_ID 저장")
    void 미가입_유저면_회원가입_후_userId_반환과_세션에_USER_ID_저장() {
        // given
        AuthLoginRequestDto req = new AuthLoginRequestDto("code", OauthProvider.KAKAO);

        OauthUser oauthUser = new OauthUser(
                OauthProvider.KAKAO,
                "kakao-999",
                "new@test.com",
                "newbie",
                "http://img"
        );

        given(clients.get(OauthProvider.KAKAO)).willReturn(kakaoClient);
        given(kakaoClient.fetchUser("code")).willReturn(oauthUser);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        // when
        authService.login(req, servletRequest);

        // then
        assertThat(userJpaRepository.findByProviderAndProviderId(OauthProvider.KAKAO, "kakao-999"))
                .isPresent();

        // then
        HttpSession session = servletRequest.getSession(false);
        assertThat(session).isNotNull();
    }

    @Test
    @DisplayName("이미 가입된 유저면 신규 저장 없이 기존 userId 반환과 세션에 USER_ID 저장")
    void 이미_가입된_유저면_신규_저장_없이_기존_userId_반환과_세션에_USER_ID_저장() {
        // given
        User existing = User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId("kakao-123")
                .email("test@test.com")
                .nickname("nick")
                .profileUrl("http://img")
                .build();
        User saved = userJpaRepository.save(existing);

        AuthLoginRequestDto req = new AuthLoginRequestDto("code", OauthProvider.KAKAO);

        OauthUser oauthUser = new OauthUser(
                OauthProvider.KAKAO,
                "kakao-123",
                "test@test.com",
                "nick",
                "http://img"
        );

        given(clients.get(OauthProvider.KAKAO)).willReturn(kakaoClient);

        given(kakaoClient.fetchUser("code")).willReturn(oauthUser);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        // when
        AuthLoginResponseDto res = authService.login(req, servletRequest);

        // then
        assertThat(userJpaRepository.count()).isEqualTo(1);

        // then
        HttpSession session = servletRequest.getSession(false);
        assertThat(session.getAttribute(SessionManager.SESSION_KEY)).isEqualTo(saved.getId());
    }
}
