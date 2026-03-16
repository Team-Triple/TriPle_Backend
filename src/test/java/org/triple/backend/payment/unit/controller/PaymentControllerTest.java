package org.triple.backend.payment.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.auth.session.UserIdentityResolver;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.invoice.exception.InvoiceErrorCode;
import org.triple.backend.payment.controller.PaymentController;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.dto.request.PaymentCreateReq;
import org.triple.backend.payment.dto.response.PaymentCreateRes;
import org.triple.backend.payment.dto.response.PaymentCursorRes;
import org.triple.backend.payment.entity.PaymentMethod;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.entity.PgProvider;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.service.PaymentService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest extends ControllerTest {

    private static final String CSRF_TOKEN = "test-csrf-token";

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @MockitoBean
    private UserIdentityResolver userIdentityResolver;

    @BeforeEach
    void setUp() {
        when(userIdentityResolver.resolve(any())).thenReturn(1L);
    }

    @Test
    @DisplayName("로그인 사용자는 결제 생성 요청이 가능하다")
    void 로그인_사용자는_결제_생성_요청이_가능하다() throws Exception {
        mockCsrfValid();
        given(paymentService.create(any(PaymentCreateReq.class), eq(1L), eq(1L)))
                .willReturn(new PaymentCreateRes("order-1", "test-order", new BigDecimal("10000")));

        mockMvc.perform(post("/payments/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 10000,
                                  "name": "test-order"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.orderName").value("test-order"))
                .andExpect(jsonPath("$.amount").value(10000));

        verify(paymentService).create(any(PaymentCreateReq.class), eq(1L), eq(1L));
    }

    @Test
    @DisplayName("로그인 사용자는 결제 승인 이벤트 요청이 가능하다")
    void 로그인_사용자는_결제_승인_이벤트_요청이_가능하다() throws Exception {
        mockCsrfValid();

        mockMvc.perform(post("/payments/{invoiceId}/confirm", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "CARD",
                                  "orderId": "order-1",
                                  "paymentKey": "payment-key-1",
                                  "requestedAmount": 10000,
                                  "pgProvider": "TOSS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(paymentService).processPaymentEvent(any(PaymentConfirmReq.class), eq(1L), eq(1L));
    }

    @Test
    @DisplayName("비로그인 사용자가 결제 생성 요청 시 401을 반환한다")
    void 비로그인_사용자가_결제_생성_요청_시_401을_반환한다() throws Exception {
        mockMvc.perform(post("/payments/{invoiceId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 10000,
                                  "name": "test-order"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());

        verify(paymentService, never()).create(any(PaymentCreateReq.class), any(), any());
    }

    @Test
    @DisplayName("CSRF 토큰이 유효하지 않으면 결제 생성 요청 시 403을 반환한다")
    void CSRF_토큰이_유효하지_않으면_결제_생성_요청_시_403을_반환한다() throws Exception {
        given(csrfTokenManager.isValid(any(HttpServletRequest.class), anyString())).willReturn(false);

        mockMvc.perform(post("/payments/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 10000,
                                  "name": "test-order"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());

        verify(paymentService, never()).create(any(PaymentCreateReq.class), any(), any());
    }

    @Test
    @DisplayName("결제 승인 이벤트 요청 바디가 유효하지 않으면 400을 반환한다")
    void 결제_승인_이벤트_요청_바디가_유효하지_않으면_400을_반환한다() throws Exception {
        mockCsrfValid();

        mockMvc.perform(post("/payments/{invoiceId}/confirm", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": " ",
                                  "orderId": "order-1",
                                  "paymentKey": "payment-key-1",
                                  "requestedAmount": 0,
                                  "pgProvider": "TOSS"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(paymentService, never()).processPaymentEvent(any(PaymentConfirmReq.class), any(), any());
    }

    @Test
    @DisplayName("결제 생성 권한이 없으면 403을 반환한다")
    void 결제_생성_권한이_없으면_403을_반환한다() throws Exception {
        mockCsrfValid();
        given(paymentService.create(any(PaymentCreateReq.class), eq(1L), eq(1L)))
                .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_NOT_ALLOWED));

        mockMvc.perform(post("/payments/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 10000,
                                  "name": "test-order"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(PaymentErrorCode.PAYMENT_NOT_ALLOWED.getMessage()));
    }

    @Test
    @DisplayName("존재하지 않는 청구서로 결제 생성 요청 시 404를 반환한다")
    void 존재하지_않는_청구서로_결제_생성_요청_시_404를_반환한다() throws Exception {
        mockCsrfValid();
        given(paymentService.create(any(PaymentCreateReq.class), eq(1L), eq(1L)))
                .willThrow(new BusinessException(InvoiceErrorCode.NOT_FOUND_INVOICE));

        mockMvc.perform(post("/payments/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 10000,
                                  "name": "test-order"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(InvoiceErrorCode.NOT_FOUND_INVOICE.getMessage()));
    }

    @Test
    @DisplayName("로그인 사용자는 결제 목록 조회가 가능하다")
    void 로그인_사용자는_결제_목록_조회가_가능하다() throws Exception {
        PaymentCursorRes response = new PaymentCursorRes(
                List.of(
                        new PaymentCursorRes.PaymentSummaryDto(
                                10L,
                                200L,
                                "test-name",
                                PgProvider.TOSS,
                                PaymentMethod.TRANSFER,
                                PaymentStatus.READY,
                                new BigDecimal("3000"),
                                null,
                                LocalDateTime.of(2030, 3, 20, 12, 0),
                                null
                        )
                ),
                null,
                false
        );
        given(paymentService.search(eq("trip"), eq(null), eq(10), eq(1L))).willReturn(response);

        mockMvc.perform(get("/payments")
                        .with(loginSession())
                        .param("keyword", "trip")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].paymentId").value(10))
                .andExpect(jsonPath("$.items[0].invoiceId").value(200))
                .andExpect(jsonPath("$.items[0].name").value("test-name"))
                .andExpect(jsonPath("$.nextCursor").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(paymentService).search("trip", null, 10, 1L);
    }

    private void mockCsrfValid() {
        when(csrfTokenManager.isValid(any(HttpServletRequest.class), anyString())).thenReturn(true);
    }

    private RequestPostProcessor loginSession() {
        return request -> {
            request.getSession(true).setAttribute(SessionManager.SESSION_KEY, "encrypted-public-uuid");
            return request;
        };
    }

    private RequestPostProcessor loginSessionAndCsrf() {
        return request -> {
            request.getSession(true).setAttribute(SessionManager.SESSION_KEY, "encrypted-public-uuid");
            request.getSession().setAttribute(CsrfTokenManager.CSRF_TOKEN_KEY, CSRF_TOKEN);
            request.addHeader(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN);
            return request;
        };
    }
}
