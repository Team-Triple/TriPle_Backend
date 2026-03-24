package org.triple.backend.global.unit.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.triple.backend.global.log.RequestMdcFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestMdcFilterTest {

    @AfterEach
    void clearMdcAfterTest() {
        MDC.clear();
    }

    @Test
    @DisplayName("request metadata is added to MDC")
    void setRequestMdc() throws Exception {
        RequestMdcFilter filter = new RequestMdcFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("traceId")).isNotBlank();
            assertThat(MDC.get("method")).isEqualTo("GET");
            assertThat(MDC.get("path")).isEqualTo("/users/me");
        });

        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    @DisplayName("mdc is cleared when filter chain throws")
    void clearMdcWhenFilterChainThrows() {
        RequestMdcFilter filter = new RequestMdcFilter();
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
