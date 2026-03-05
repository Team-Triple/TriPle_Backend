package org.triple.backend.auth.unit.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.auth.session.UserIdentityResolver;
import org.triple.backend.auth.session.UuidCrypto;
import org.triple.backend.global.error.BusinessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

class SessionManagerTest {

    private final UserIdentityResolver userIdentityResolver = mock(UserIdentityResolver.class);
    private final UuidCrypto uuidCrypto = mock(UuidCrypto.class);
    private final SessionManager sessionManager = new SessionManager(userIdentityResolver, uuidCrypto);

    @Test
    @DisplayName("login은 세션에 UUID를 저장한다")
    void login은_세션에_UUID를_저장한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID publicUuid = UUID.randomUUID();
        given(uuidCrypto.encrypt(publicUuid)).willReturn("encrypted-session-principal");

        // when
        sessionManager.login(request, publicUuid);

        // then
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute(SessionManager.SESSION_KEY)).isEqualTo("encrypted-session-principal");
    }

    @Test
    @DisplayName("세션이 없으면 getUserId는 null을 반환한다")
    void 세션이_없으면_getUserId는_null을_반환한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        Long userId = sessionManager.getUserId(request);

        // then
        assertThat(userId).isNull();
    }

    @Test
    @DisplayName("세션 principal이 있으면 UserIdentityResolver로 userId를 조회한다")
    void 세션_principal이_있으면_UserIdentityResolver로_userId를_조회한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID publicUuid = UUID.randomUUID();
        request.getSession(true).setAttribute(SessionManager.SESSION_KEY, publicUuid);
        given(userIdentityResolver.resolve(publicUuid)).willReturn(7L);

        // when
        Long userId = sessionManager.getUserId(request);

        // then
        assertThat(userId).isEqualTo(7L);
        then(userIdentityResolver).should(times(1)).resolve(publicUuid);
    }

    @Test
    @DisplayName("resolveUserId는 UserIdentityResolver에 위임한다")
    void resolveUserId는_UserIdentityResolver에_위임한다() {
        // given
        UUID publicUuid = UUID.randomUUID();
        given(userIdentityResolver.resolve(publicUuid)).willReturn(9L);

        // when
        Long userId = sessionManager.resolveUserId(publicUuid);

        // then
        assertThat(userId).isEqualTo(9L);
        then(userIdentityResolver).should(times(1)).resolve(publicUuid);
    }

    @Test
    @DisplayName("세션에 사용자 정보가 없으면 getUserIdOrThrow는 UNAUTHORIZED 예외를 던진다")
    void 세션에_사용자_정보가_없으면_getUserIdOrThrow는_UNAUTHORIZED_예외를_던진다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when & then
        assertThatThrownBy(() -> sessionManager.getUserIdOrThrow(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("logout은 세션을 무효화한다")
    void logout은_세션을_무효화한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(SessionManager.SESSION_KEY, UUID.randomUUID());
        given(userIdentityResolver.resolve(any())).willReturn(1L);

        // when
        sessionManager.logout(request);

        // then
        assertThat(request.getSession(false)).isNull();
    }
}
