package org.triple.backend.global.unit.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.triple.backend.auth.session.UserIdentityResolver;
import org.triple.backend.global.log.MaskUtil;
import org.triple.backend.global.log.RequestMdcFilter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RequestMdcFilterTest {

    @AfterEach
    void clearMdcAfterTest() {
        MDC.clear();
    }

    @Test
    @DisplayName("세션이 없으면 익명 MDC 값을 설정한다")
    void setAnonymousMdcWhenNoSession() throws Exception {
        UserIdentityResolver userIdentityResolver = mock(UserIdentityResolver.class);
        RequestMdcFilter filter = new RequestMdcFilter(userIdentityResolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("traceId")).isNotBlank();
            assertThat(MDC.get("method")).isEqualTo("GET");
            assertThat(MDC.get("path")).isEqualTo("/users/me");
            assertThat(MDC.get("userUuid")).isEqualTo(MaskUtil.maskString("anonymous"));
            assertThat(MDC.get("sessionId")).isEqualTo("null");
        });

        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    @DisplayName("세션이 있으면 userUuid와 sessionId를 마스킹해 MDC에 설정한다")
    void setMaskedMdcWhenSessionExists() throws Exception {
        UserIdentityResolver userIdentityResolver = mock(UserIdentityResolver.class);
        RequestMdcFilter filter = new RequestMdcFilter(userIdentityResolver);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UUID publicUuid = UUID.randomUUID();
        request.getSession(true).setAttribute(USER_SESSION_KEY, publicUuid);
        String sessionId = request.getSession(false).getId();
        given(userIdentityResolver.parsePublicUuid(any())).willReturn(publicUuid);

        filter.doFilter(request, response, (req, res) -> {
            String expectedUserUuid = MaskUtil.maskString(MaskUtil.maskString(publicUuid.toString()));
            assertThat(MDC.get("traceId")).isNotBlank();
            assertThat(MDC.get("method")).isEqualTo("POST");
            assertThat(MDC.get("path")).isEqualTo("/auth/login");
            assertThat(MDC.get("userUuid")).isEqualTo(expectedUserUuid);
            assertThat(MDC.get("sessionId")).isEqualTo(MaskUtil.maskString(sessionId));
        });

        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    @DisplayName("하위 체인 예외가 발생해도 MDC를 비운다")
    void clearMdcWhenFilterChainThrows() {
        UserIdentityResolver userIdentityResolver = mock(UserIdentityResolver.class);
        RequestMdcFilter filter = new RequestMdcFilter(userIdentityResolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() ->
                filter.doFilter(request, response, (req, res) -> {
                    throw new IllegalStateException("boom");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.getCopyOfContextMap()).isNull();
    }
}
