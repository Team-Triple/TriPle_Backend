package org.triple.backend.payment.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.payment.controller.PaymentController;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.dto.response.PaymentConfirmRes;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.service.PaymentServiceFacade;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN_KEY;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest extends ControllerTest {

    @MockitoBean
    private PaymentServiceFacade paymentServiceFacade;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @Test
    @DisplayName("결제 승인 요청이 성공하면 200과 결제 정보를 반환한다")
    void 결제_승인_요청이_성공하면_200과_결제_정보를_반환한다() throws Exception {
        mockCsrfValid();
        PaymentConfirmRes response = new PaymentConfirmRes(
            "order-1",
            new BigDecimal("10000"),
            "https://receipt.url",
            PaymentStatus.DONE
        );
        given(paymentServiceFacade.confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L)))
            .willReturn(response);

        mockMvc.perform(post("/payment/{invoiceId}", 1L)
                .with(loginSessionAndCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value("order-1"))
            .andExpect(jsonPath("$.amount").value(10000))
            .andExpect(jsonPath("$.receiptUrl").value("https://receipt.url"))
            .andExpect(jsonPath("$.status").value("DONE"));

        verify(paymentServiceFacade, times(1))
            .confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L));
    }

    @Test
    @DisplayName("로그인하지 않은 사용자가 결제 승인 요청하면 401을 반환한다")
    void 로그인하지_않은_사용자가_결제_승인_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/payment/{invoiceId}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmBody()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(AuthErrorCode.UNAUTHORIZED.getMessage()));

        verify(paymentServiceFacade, never())
            .confirm(any(PaymentConfirmReq.class), anyLong(), anyLong());
    }

    @Test
    @DisplayName("CSRF 토큰이 유효하지 않으면 403을 반환한다")
    void CSRF_토큰이_유효하지_않으면_403을_반환한다() throws Exception {
        given(csrfTokenManager.isValid(any(HttpServletRequest.class), any(String.class)))
            .willReturn(false);

        mockMvc.perform(post("/payment/{invoiceId}", 1L)
                .with(loginSessionAndCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmBody()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(AuthErrorCode.INVALID_CSRF_TOKEN.getMessage()));

        verify(paymentServiceFacade, never())
            .confirm(any(PaymentConfirmReq.class), anyLong(), anyLong());
    }

    @Test
    @DisplayName("결제를 찾을 수 없으면 404를 반환한다")
    void 결제를_찾을_수_없으면_404를_반환한다() throws Exception {
        mockCsrfValid();
        given(paymentServiceFacade.confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L)))
            .willThrow(new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT));

        mockMvc.perform(post("/payment/{invoiceId}", 1L)
                .with(loginSessionAndCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmBody()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(PaymentErrorCode.NOT_FOUND_PAYMENT.getMessage()));

        verify(paymentServiceFacade, times(1))
            .confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L));
    }

    @Test
    @DisplayName("이미 처리된 결제면 409를 반환한다")
    void 이미_처리된_결제면_409를_반환한다() throws Exception {
        mockCsrfValid();
        given(paymentServiceFacade.confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L)))
            .willThrow(new BusinessException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT));

        mockMvc.perform(post("/payment/{invoiceId}", 1L)
                .with(loginSessionAndCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmBody()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT.getMessage()));

        verify(paymentServiceFacade, times(1))
            .confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L));
    }

    @Test
    @DisplayName("승인 금액이 올바르지 않으면 400을 반환한다")
    void 승인_금액이_올바르지_않으면_400을_반환한다() throws Exception {
        mockCsrfValid();
        given(paymentServiceFacade.confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L)))
            .willThrow(new BusinessException(PaymentErrorCode.ILLEGAL_AMOUNT));

        mockMvc.perform(post("/payment/{invoiceId}", 1L)
                .with(loginSessionAndCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmBody()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(PaymentErrorCode.ILLEGAL_AMOUNT.getMessage()));

        verify(paymentServiceFacade, times(1))
            .confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L));
    }

    @Test
    @DisplayName("결제 승인 처리 실패면 500을 반환한다")
    void 결제_승인_처리_실패면_500을_반환한다() throws Exception {
        mockCsrfValid();
        given(paymentServiceFacade.confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L)))
            .willThrow(new BusinessException(PaymentErrorCode.CONFIRM_FAILED));

        mockMvc.perform(post("/payment/{invoiceId}", 1L)
                .with(loginSessionAndCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmBody()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value(PaymentErrorCode.CONFIRM_FAILED.getMessage()));

        verify(paymentServiceFacade, times(1))
            .confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L));
    }

    @Test
    @DisplayName("잘못된 인자가 전달되면 400을 반환한다")
    void 잘못된_인자가_전달되면_400을_반환한다() throws Exception {
        mockCsrfValid();
        given(paymentServiceFacade.confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L)))
            .willThrow(new IllegalArgumentException("invalid payment confirm request"));

        mockMvc.perform(post("/payment/{invoiceId}", 1L)
                .with(loginSessionAndCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmBody()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("invalid payment confirm request"));

        verify(paymentServiceFacade, times(1))
            .confirm(any(PaymentConfirmReq.class), eq(1L), eq(1L));
    }

    @Test
    @DisplayName("invoiceId 경로 변수가 숫자가 아니면 400을 반환한다")
    void invoiceId_경로_변수가_숫자가_아니면_400을_반환한다() throws Exception {
        mockCsrfValid();

        mockMvc.perform(post("/payment/{invoiceId}", "not-number")
                .with(loginSessionAndCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmBody()))
            .andExpect(status().isBadRequest());

        verify(paymentServiceFacade, never())
            .confirm(any(PaymentConfirmReq.class), anyLong(), anyLong());
    }

    @Test
    @DisplayName("요청 바디 JSON 형식이 잘못되면 400을 반환한다")
    void 요청_바디_JSON_형식이_잘못되면_400을_반환한다() throws Exception {
        mockCsrfValid();

        String malformedBody = """
                {
                  "method": "CARD",
                  "orderId": "order-1",
                  "paymentKey": "payment-key",
                  "approvedAmount": 10000,
                }
                """;

        mockMvc.perform(post("/payment/{invoiceId}", 1L)
                .with(loginSessionAndCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedBody))
            .andExpect(status().isBadRequest());

        verify(paymentServiceFacade, never())
            .confirm(any(PaymentConfirmReq.class), anyLong(), anyLong());
    }

    private void mockCsrfValid() {
        when(csrfTokenManager.isValid(any(HttpServletRequest.class), any(String.class))).thenReturn(true);
    }

    private RequestPostProcessor loginSessionAndCsrf() {
        return request -> {
            request.getSession(true).setAttribute(USER_SESSION_KEY, 1L);
            request.getSession().setAttribute(CSRF_TOKEN_KEY, CSRF_TOKEN);
            request.addHeader(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN);
            return request;
        };
    }

    private String validConfirmBody() {
        return """
                {
                  "method": "CARD",
                  "orderId": "order-1",
                  "paymentKey": "payment-key",
                  "approvedAmount": 10000,
                  "pgProvider": "TOSS"
                }
                """;
    }
}
