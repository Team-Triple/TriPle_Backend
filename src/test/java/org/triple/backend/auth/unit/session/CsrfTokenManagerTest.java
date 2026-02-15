package org.triple.backend.auth.unit.session;


import jakarta.servlet.http.HttpSession;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.triple.backend.auth.session.CsrfTokenManager;

class CsrfTokenManagerTest {
    private static final String CSRF_TOKEN_KEY = "CSRF_TOKEN";

    @Test
    @DisplayName("세션에 토큰이 없을 경우, 생성 후 세션에 저장되어야 한다.")
    void 토큰_생성_반환() {
        //given
        CsrfTokenManager csrfTokenManager = new CsrfTokenManager();
        MockHttpServletRequest request = new MockHttpServletRequest();

        //when
        String csrfToken = csrfTokenManager.getOrCreateToken(request);

        //then
        HttpSession session = request.getSession();
        Assertions.assertThat(session.getAttribute(CSRF_TOKEN_KEY)).isNotNull();
        Assertions.assertThat(session.getAttribute(CSRF_TOKEN_KEY)).isEqualTo(csrfToken);
    }

    @Test
    @DisplayName("세션에 토큰이 존재할 경우, 해당 토큰을 재사용한다.")
    void 토큰_반환() {
        //given
        CsrfTokenManager csrfTokenManager = new CsrfTokenManager();
        String csrfToken = "test-token";

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CSRF_TOKEN_KEY, csrfToken);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        //when
        String targetCsrfToken = csrfTokenManager.getOrCreateToken(request);

        //then
        Assertions.assertThat(targetCsrfToken).isNotNull();
        Assertions.assertThat(targetCsrfToken).isEqualTo(csrfToken);
    }

    @Test
    @DisplayName("받은 토큰이 Blank 상태일 경우, false를 반환한다.")
    void 빈_토큰_false_반환() {
        //given
        CsrfTokenManager csrfTokenManager = new CsrfTokenManager();
        MockHttpServletRequest request = new MockHttpServletRequest();
        String requestedToken = "";

        //when
        boolean answer = csrfTokenManager.isValid(request, requestedToken);

        //then
        Assertions.assertThat(answer).isFalse();
    }

    @Test
    @DisplayName("해당 요청을 보낸 세션ID의 세션이 없을 경우, false를 반환한다.")
    void 빈_세션_false_반환() {
        //given
        CsrfTokenManager csrfTokenManager = new CsrfTokenManager();
        MockHttpServletRequest request = new MockHttpServletRequest();
        String requestedToken = "test-token";

        //when
        boolean answer = csrfTokenManager.isValid(request, requestedToken);

        //then
        Assertions.assertThat(answer).isFalse();
    }

    @Test
    @DisplayName("요청 토큰과 세션 내의 토큰 값이 같지 않을 경우, false를 반환한다.")
    void 요청_토큰_세션_토큰_다름_false_반환() {
        //given
        CsrfTokenManager csrfTokenManager = new CsrfTokenManager();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CSRF_TOKEN_KEY, "original-token");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        String requestedToken = "different-token";

        //when
        boolean answer = csrfTokenManager.isValid(request, requestedToken);

        //then
        Assertions.assertThat(answer).isFalse();
    }
}