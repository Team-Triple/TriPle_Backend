package org.triple.backend.payment.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.global.error.ErrorCode;
import org.triple.backend.invoice.exception.InvoiceErrorCode;
import org.triple.backend.payment.controller.PaymentController;
import org.triple.backend.payment.dto.request.PaymentCreateReq;
import org.triple.backend.payment.dto.response.PaymentCreateRes;
import org.triple.backend.payment.dto.response.PaymentSearchRes;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.service.PaymentService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN_KEY;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest extends ControllerTest {

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @Test
    @DisplayName("로그인한 사용자는 결제 생성 요청을 할 수 있다.")
    void 로그인한_사용자는_결제_생성_요청을_할_수_있다() throws Exception {
        // given
        PaymentCreateRes response = new PaymentCreateRes("order-1", "제주 렌트비", new BigDecimal("10000"));
        given(paymentService.create(any(PaymentCreateReq.class), eq(1L), eq(1L))).willReturn(response);
        mockCsrfValid();

        // when & then
        mockMvc.perform(post("/payments/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.orderName").value("제주 렌트비"))
                .andExpect(jsonPath("$.amount").value(10000))
                .andDo(document("payments/create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("결제 대상 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("amount").description("요청 결제 금액"),
                                fieldWithPath("name").description("주문명")
                        ),
                        responseFields(
                                fieldWithPath("orderId").description("주문 ID"),
                                fieldWithPath("orderName").description("주문명"),
                                fieldWithPath("amount").description("요청 결제 금액")
                        )
                ));

        verify(paymentService, times(1)).create(any(PaymentCreateReq.class), eq(1L), eq(1L));
    }

    @Test
    @DisplayName("비로그인 사용자가 결제 생성 요청을 하면 401을 반환한다.")
    void 비로그인_사용자가_결제_생성_요청을_하면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/payments/{invoiceId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("payments/create-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("결제 대상 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("amount").description("요청 결제 금액"),
                                fieldWithPath("name").description("주문명")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(paymentService, never()).create(any(PaymentCreateReq.class), any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("로그인 사용자의 CSRF 토큰이 유효하지 않으면 403을 반환한다.")
    void 로그인_사용자의_CSRF_토큰이_유효하지_않으면_403을_반환한다() throws Exception {
        given(csrfTokenManager.isValid(any(HttpServletRequest.class), any(String.class))).willReturn(false);

        mockMvc.perform(post("/payments/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF 토큰이 유효하지 않습니다."))
                .andDo(document("payments/create-fail-invalid-csrf-token",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("결제 대상 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("amount").description("요청 결제 금액"),
                                fieldWithPath("name").description("주문명")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(paymentService, never()).create(any(PaymentCreateReq.class), any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("결제 생성 요청 바디가 유효하지 않으면 400을 반환한다.")
    void 결제_생성_요청_바디가_유효하지_않으면_400을_반환한다() throws Exception {
        mockCsrfValid();

        String invalidBody = """
                {
                  "amount": 0,
                  "name": " "
                }
                """;

        mockMvc.perform(post("/payments/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andDo(document("payments/create-fail-bad-request",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("결제 대상 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("amount").description("요청 결제 금액"),
                                fieldWithPath("name").description("주문명")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(paymentService, never()).create(any(PaymentCreateReq.class), any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("결제 진행 불가능한 청구서 요청 시 403을 반환한다.")
    void 결제_진행_불가능한_청구서_요청_시_403을_반환한다() throws Exception {
        assertBusinessFailure(
                PaymentErrorCode.PAYMENT_NOT_ALLOWED,
                status().isForbidden(),
                "payments/create-fail-payment-not-allowed"
        );
    }

    @Test
    @DisplayName("존재하지 않는 청구서로 요청하면 404를 반환한다.")
    void 존재하지_않는_청구서로_요청하면_404를_반환한다() throws Exception {
        assertBusinessFailure(
                InvoiceErrorCode.NOT_FOUND_INVOICE,
                status().isNotFound(),
                "payments/create-fail-not-found-invoice"
        );
    }

    @Test
    @DisplayName("이미 결제가 완료된 대상에 결제를 생성하면 409를 반환한다.")
    void 이미_결제가_완료된_대상에_결제를_생성하면_409를_반환한다() throws Exception {
        assertBusinessFailure(
                PaymentErrorCode.PAYMENT_ALREADY_COMPLETED,
                status().isConflict(),
                "payments/create-fail-payment-already-completed"
        );
    }

    @Test
    @DisplayName("요청 결제 금액이 남은 금액을 초과하면 409를 반환한다.")
    void 요청_결제_금액이_남은_금액을_초과하면_409를_반환한다() throws Exception {
        assertBusinessFailure(
                PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING,
                status().isConflict(),
                "payments/create-fail-payment-amount-exceeds-remaining"
        );
    }

    @Test
    @DisplayName("이미 진행중인 결제가 있으면 403을 반환한다.")
    void 이미_진행중인_결제가_있으면_403을_반환한다() throws Exception {
        assertBusinessFailure(
                PaymentErrorCode.PAYMENT_ALREADY_IS_ACTIVE,
                status().isForbidden(),
                "payments/create-fail-payment-already-active"
        );
    }

    @Test
    @DisplayName("중복 결제 생성 요청이면 409를 반환한다.")
    void 중복_결제_생성_요청이면_409를_반환한다() throws Exception {
        assertBusinessFailure(
                PaymentErrorCode.DUPLICATED_PAYMENT,
                status().isConflict(),
                "payments/create-fail-duplicated-payment"
        );
    }

    @Test
    @DisplayName("로그인한 사용자는 invoiceId로 결제 내역을 조회할 수 있다.")
    void loginUserCanSearchPaymentsByInvoiceId() throws Exception {
        PaymentSearchRes response = new PaymentSearchRes(
                1L,
                List.of(
                        new PaymentSearchRes.PaymentDetail(
                                1L,
                                "payer-one",
                                "order-2",
                                new BigDecimal("4000"),
                                org.triple.backend.payment.entity.PaymentStatus.IN_PROGRESS,
                                LocalDateTime.of(2030, 3, 1, 10, 30)
                        ),
                        new PaymentSearchRes.PaymentDetail(
                                2L,
                                "payer-two",
                                "order-1",
                                new BigDecimal("3000"),
                                org.triple.backend.payment.entity.PaymentStatus.READY,
                                LocalDateTime.of(2030, 3, 1, 9, 30)
                        )
                )
        );
        given(paymentService.search(eq(1L), eq(1L))).willReturn(response);

        mockMvc.perform(get("/payments/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(1L))
                .andExpect(jsonPath("$.payments.length()").value(2))
                .andExpect(jsonPath("$.payments[0].paymentId").value(2L))
                .andExpect(jsonPath("$.payments[0].userId").value(1L))
                .andExpect(jsonPath("$.payments[0].userNickname").value("payer-one"))
                .andExpect(jsonPath("$.payments[0].orderId").value("order-2"))
                .andExpect(jsonPath("$.payments[0].requestedAmount").value(4000))
                .andExpect(jsonPath("$.payments[0].paymentStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.payments[0].requestedAt").value("2030-03-01T10:30:00"))
                .andDo(document("payments/search",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("조회 대상 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("invoiceId").description("청구서 ID"),
                                fieldWithPath("payments").description("결제 목록"),
                                fieldWithPath("payments[].paymentId").description("결제 ID"),
                                fieldWithPath("payments[].userId").description("결제 요청 사용자 ID"),
                                fieldWithPath("payments[].userNickname").description("결제 요청 사용자 닉네임"),
                                fieldWithPath("payments[].orderId").description("주문 ID"),
                                fieldWithPath("payments[].requestedAmount").description("요청 결제 금액"),
                                fieldWithPath("payments[].paymentStatus").description("결제 상태"),
                                fieldWithPath("payments[].requestedAt").description("결제 요청 시각")
                        )
                ));

        verify(paymentService, times(1)).search(1L, 1L);
    }

    @Test
    @DisplayName("비로그인 사용자가 결제 조회를 요청하면 401을 반환한다.")
    void searchPaymentsWithoutLoginReturns401() throws Exception {
        mockMvc.perform(get("/payments/{invoiceId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("payments/search-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("조회 대상 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(paymentService, never()).search(any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("조회 권한이 없는 사용자가 결제 조회를 요청하면 403을 반환한다.")
    void searchPaymentsWithoutPermissionReturns403() throws Exception {
        assertSearchBusinessFailure(
                PaymentErrorCode.PAYMENT_SEARCH_NOT_ALLOWED,
                status().isForbidden(),
                "payments/search-fail-payment-search-not-allowed"
        );
    }

    @Test
    @DisplayName("존재하지 않는 invoiceId로 결제 조회를 요청하면 404를 반환한다.")
    void searchPaymentsWithUnknownInvoiceIdReturns404() throws Exception {
        assertSearchBusinessFailure(
                InvoiceErrorCode.NOT_FOUND_INVOICE,
                status().isNotFound(),
                "payments/search-fail-not-found-invoice"
        );
    }

    private void assertBusinessFailure(
            final ErrorCode errorCode,
            final ResultMatcher statusMatcher,
            final String snippetId
    ) throws Exception {
        mockCsrfValid();
        given(paymentService.create(any(PaymentCreateReq.class), eq(1L), eq(1L)))
                .willThrow(new BusinessException(errorCode));

        mockMvc.perform(post("/payments/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(statusMatcher)
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                .andDo(document(snippetId,
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("결제 대상 청구서 ID")
                        ),
                        requestFields(
                                fieldWithPath("amount").description("요청 결제 금액"),
                                fieldWithPath("name").description("주문명")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(paymentService, times(1)).create(any(PaymentCreateReq.class), eq(1L), eq(1L));
    }

    private void assertSearchBusinessFailure(
            final ErrorCode errorCode,
            final ResultMatcher statusMatcher,
            final String snippetId
    ) throws Exception {
        given(paymentService.search(eq(1L), eq(1L)))
                .willThrow(new BusinessException(errorCode));

        mockMvc.perform(get("/payments/{invoiceId}", 1L)
                        .with(loginSessionAndCsrf()))
                .andExpect(statusMatcher)
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                .andDo(document(snippetId,
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("invoiceId").description("조회 대상 청구서 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        verify(paymentService, times(1)).search(eq(1L), eq(1L));
    }

    private String validBody() {
        return """
                {
                  "amount": 10000,
                  "name": "제주 렌트비"
                }
                """;
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
}
