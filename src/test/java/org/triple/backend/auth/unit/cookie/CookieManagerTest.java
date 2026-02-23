package org.triple.backend.auth.unit.cookie;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.triple.backend.auth.cookie.CookieManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class CookieManagerTest {

    private final CookieManager cookieManager = new CookieManager();

    @Test
    @DisplayName("addLoginCookie는 login_status=true 쿠키를 Set-Cookie 헤더로 추가한다")
    void addLoginCookie는_login_status_true_쿠키를_Set_Cookie_헤더로_추가한다() {
        // given
        HttpServletResponse response = mock(HttpServletResponse.class);
        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);

        // when
        cookieManager.addLoginCookie(response);

        // then
        verify(response, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), headerValueCaptor.capture());

        String setCookie = headerValueCaptor.getValue();
        assertThat(setCookie).contains("login_status=true");
        assertThat(setCookie).contains("Path=/");
        assertThat(setCookie).contains("Max-Age=86400");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("SameSite=None");
        assertThat(setCookie).doesNotContainIgnoringCase("HttpOnly"); // httpOnly(false)면 보통 HttpOnly 속성이 안 붙음
    }

    @Test
    @DisplayName("clearLoginCookie는 login_status 쿠키를 만료시킨다")
    void clearLoginCookie는_login_status_쿠키를_만료시킨다() {
        // given
        HttpServletResponse response = mock(HttpServletResponse.class);
        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);

        // when
        cookieManager.clearLoginCookie(response);

        // then
        verify(response, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), headerValueCaptor.capture());

        String setCookie = headerValueCaptor.getValue();
        assertThat(setCookie).contains("login_status=");
        assertThat(setCookie).contains("Path=/");
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("SameSite=None");
        assertThat(setCookie).doesNotContainIgnoringCase("HttpOnly");
    }

    @Test
    @DisplayName("clearSessionCookie는 JSESSIONID 쿠키를 만료시킨다")
    void clearSessionCookie는_JSESSIONID_쿠키를_만료시킨다() {
        // given
        HttpServletResponse response = mock(HttpServletResponse.class);
        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);

        // when
        cookieManager.clearSessionCookie(response);

        // then
        verify(response, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), headerValueCaptor.capture());

        String setCookie = headerValueCaptor.getValue();
        assertThat(setCookie).contains("JSESSIONID=");
        assertThat(setCookie).contains("Path=/");
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("SameSite=None");
        assertThat(setCookie).contains("HttpOnly");
    }
}
