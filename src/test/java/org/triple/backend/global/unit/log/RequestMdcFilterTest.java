package org.triple.backend.global.unit.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.global.log.RequestMdcFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RequestMdcFilterTest {

    @AfterEach
    void 테스트_후_MDC_정리() {
        MDC.clear();
    }

    @Test
    @DisplayName("세션이 없으면 MDC에 기본값을 설정한다")
    void 세션없음_MDC_기본값_설정() throws Exception {
        SessionManager sessionManager = mock(SessionManager.class);
        RequestMdcFilter filter = new RequestMdcFilter(sessionManager);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(sessionManager.getUserId(any())).willReturn(null);

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("traceId")).isNotBlank();
            assertThat(MDC.get("method")).isEqualTo("GET");
            assertThat(MDC.get("path")).isEqualTo("/users/me");
            assertThat(MDC.get("userId")).isEqualTo("anonymous");
            assertThat(MDC.get("sessionId")).isNull();
        });

        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    @DisplayName("세션이 있으면 원본 세션 값을 MDC에 설정한다")
    void 세션있음_MDC_원본값_설정() throws Exception {
        SessionManager sessionManager = mock(SessionManager.class);
        RequestMdcFilter filter = new RequestMdcFilter(sessionManager);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute(USER_SESSION_KEY, 12345L);
        String sessionId = request.getSession(false).getId();
        given(sessionManager.getUserId(any())).willReturn(12345L);

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("traceId")).isNotBlank();
            assertThat(MDC.get("method")).isEqualTo("POST");
            assertThat(MDC.get("path")).isEqualTo("/auth/login");
            assertThat(MDC.get("userId")).isEqualTo("12345");
            assertThat(MDC.get("sessionId")).isEqualTo(sessionId);
        });

        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    @DisplayName("하위 체인에서 예외가 발생해도 MDC를 비운다")
    void 하위체인_예외_발생시_MDC_정리() {
        SessionManager sessionManager = mock(SessionManager.class);
        RequestMdcFilter filter = new RequestMdcFilter(sessionManager);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(sessionManager.getUserId(any())).willReturn(null);

        assertThatThrownBy(() ->
                filter.doFilter(request, response, (req, res) -> {
                    throw new IllegalStateException("boom");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.getCopyOfContextMap()).isNull();
    }
}
