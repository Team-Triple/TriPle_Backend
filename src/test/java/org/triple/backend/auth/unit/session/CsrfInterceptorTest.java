package org.triple.backend.auth.unit.session;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.triple.backend.auth.session.CsrfInterceptor;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.global.error.BusinessException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CsrfInterceptorTest {

    static class HandlerMethodController {
        @LoginRequired
        public void test(){}
    }

    static class NoLoginRequiredHandlerMethodController {
        public void test(){}
    }

    @Test
    @DisplayName("컨트롤러 메서드 핸들러가 아닐 경우 true를 반환해야 한다.(그냥 인터셉터 통과)")
    void 일반_핸들러_그냥_통과() throws NoSuchMethodException {
        //given
        CsrfTokenManager csrfTokenManager = Mockito.mock(CsrfTokenManager.class);
        CsrfInterceptor csrfInterceptor = new CsrfInterceptor(csrfTokenManager);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        //when
        boolean answer = csrfInterceptor.preHandle(request, response, new Object());

        //then
        Assertions.assertThat(answer).isTrue();
    }

    @Test
    @DisplayName("SAFE_METHODS(CSRF 검사하지 않아도되는)일 경우 true를 반환해야 한다.")
    void SAFE_METHODS_true_반환() throws NoSuchMethodException {
        //given
        CsrfTokenManager csrfTokenManager = Mockito.mock(CsrfTokenManager.class);
        CsrfInterceptor csrfInterceptor = new CsrfInterceptor(csrfTokenManager);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new HandlerMethodController(), "test");

        //when
        boolean answer = csrfInterceptor.preHandle(request, response, handlerMethod);

        //then
        Assertions.assertThat(answer).isTrue();
    }

    @Test
    @DisplayName("@LoginRequired가 클래스나 메서드에 존재하지 않을 경우 true를 반환해야 한다.")
    void 메서드_LoginRequired_없음_true_반환() throws NoSuchMethodException {
        //given
        CsrfTokenManager csrfTokenManager = Mockito.mock(CsrfTokenManager.class);
        CsrfInterceptor csrfInterceptor = new CsrfInterceptor(csrfTokenManager);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new NoLoginRequiredHandlerMethodController(), "test");

        //when
        boolean answer = csrfInterceptor.preHandle(request, response, handlerMethod);

        //then
        Assertions.assertThat(answer).isTrue();
    }

    @Test
    @DisplayName("csrfTokenManager에서 헤더의 토큰 검증 실패 시 BusinessException을 던진다.")
    void 검증_실패_BusinessException_던짐() throws NoSuchMethodException {
        //given
        CsrfTokenManager csrfTokenManager = Mockito.mock(CsrfTokenManager.class);
        when(csrfTokenManager.isValid(any(), any())).thenReturn(false);
        CsrfInterceptor csrfInterceptor = new CsrfInterceptor(csrfTokenManager);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new HandlerMethodController(), "test");

        //when&then
        Assertions.assertThatThrownBy(() -> csrfInterceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("csrfTokenManager 정상 수행시 true를 반환해야 한다.")
    void 정상_수행() throws NoSuchMethodException {
        //given
        CsrfTokenManager csrfTokenManager = Mockito.mock(CsrfTokenManager.class);
        when(csrfTokenManager.isValid(any(), any())).thenReturn(true);
        CsrfInterceptor csrfInterceptor = new CsrfInterceptor(csrfTokenManager);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = new HandlerMethod(new HandlerMethodController(), "test");

        //when
        boolean answer = csrfInterceptor.preHandle(request, response, handlerMethod);

        //then
        Assertions.assertThat(answer).isTrue();
    }
}