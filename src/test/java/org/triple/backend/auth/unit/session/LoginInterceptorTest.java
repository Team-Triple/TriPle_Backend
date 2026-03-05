package org.triple.backend.auth.unit.session;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.session.LoginInterceptor;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.global.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.triple.backend.global.constants.AuthConstants.LOGIN_USER_ID;

public class LoginInterceptorTest {

    private final SessionManager sessionManager = mock(SessionManager.class);
    private final LoginInterceptor interceptor = new LoginInterceptor(sessionManager);

    @LoginRequired
    static class RequiredController {
        public void me() {}
    }

    static class NotRequiredController {
        public void me() {}
    }

    @Test
    @DisplayName("@LoginRequired인데 세션이 없으면 예외가 발생한다")
    void LoginRequired인데_세션이_없으면_예외가_발생한다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handler = new HandlerMethod(new RequiredController(), "me");

        given(sessionManager.getUserIdOrThrow(any(HttpServletRequest.class)))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);

        // 세션 매니저 호출됐는지
        then(sessionManager).should(times(1)).getUserIdOrThrow(any(HttpServletRequest.class));
        // attribute는 당연히 없어야 함
        assertThat(request.getAttribute(LOGIN_USER_ID)).isNull();
    }

    @Test
    @DisplayName("@LoginRequired가 사용되면 userId를 request attribute에 세팅한다")
    void LoginRequired가_사용되면_userId를_request_attribute에_세팅한다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handler = new HandlerMethod(new RequiredController(), "me");

        given(sessionManager.getUserIdOrThrow(any(HttpServletRequest.class))).willReturn(1L);

        // when
        boolean result = interceptor.preHandle(request, response, handler);

        // then
        assertThat(result).isTrue();
        assertThat(request.getAttribute(LOGIN_USER_ID)).isEqualTo(1L);
    }

    @Test
    @DisplayName("@LoginRequired가 없으면 세션 매니저를 호출하지 않고 통과한다")
    void LoginRequired가_없으면_세션_매니저를_호출하지_않고_통과한다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handler = new HandlerMethod(new NotRequiredController(), "me");

        // when
        boolean result = interceptor.preHandle(request, response, handler);

        // then
        assertThat(result).isTrue();
        assertThat(request.getAttribute(LOGIN_USER_ID)).isNull();
    }
}
