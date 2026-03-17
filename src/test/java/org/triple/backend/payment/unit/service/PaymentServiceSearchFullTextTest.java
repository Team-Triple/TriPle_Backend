package org.triple.backend.payment.unit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.payment.dto.response.PaymentCursorRes;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentMethod;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.entity.PgProvider;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.repository.PaymentEventJpaRepository;
import org.triple.backend.payment.repository.PaymentJpaRepository;
import org.triple.backend.payment.service.PaymentService;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceSearchFullTextTest {

    private static final Long USER_ID = 1L;

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private PaymentEventJpaRepository paymentEventJpaRepository;

    @Mock
    private InvoiceUserJpaRepository invoiceUserJpaRepository;

    @Mock
    private InvoiceJpaRepository invoiceJpaRepository;

    @Mock
    private UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentJpaRepository,
                paymentEventJpaRepository,
                invoiceUserJpaRepository,
                invoiceJpaRepository,
                userTravelItineraryJpaRepository
        );
    }

    @Test
    @DisplayName("검색어가 비어있으면 첫 페이지를 일반 조회한다")
    void 검색어가_비어있으면_첫_페이지를_일반_조회한다() {
        Payment p1 = newPayment(30L, 100L, "trip a");
        Payment p2 = newPayment(29L, 101L, "trip b");

        when(paymentJpaRepository.findFirstPage(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(p1, p2));

        PaymentCursorRes response = paymentService.search(" ", null, 10, USER_ID);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        verify(paymentJpaRepository).findFirstPage(eq(USER_ID), any(Pageable.class));
        verify(paymentJpaRepository, never()).findFirstPageIdsByKeywordFullText(any(), anyLong(), any(Pageable.class));
    }

    @Test
    @DisplayName("검색어가 있으면 boolean mode full-text로 첫 페이지를 조회한다")
    void 검색어가_있으면_boolean_mode_full_text로_첫_페이지를_조회한다() {
        Payment p1 = newPayment(30L, 100L, "jeju transfer");
        Payment p2 = newPayment(29L, 101L, "jeju stay");

        when(paymentJpaRepository.findFirstPageIdsByKeywordFullText(eq("+jeju* +pay*"), eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(30L, 29L));
        when(paymentJpaRepository.findAllWithInvoiceByIdInOrderByIdDesc(eq(List.of(30L, 29L))))
                .thenReturn(List.of(p1, p2));

        PaymentCursorRes response = paymentService.search("jeju pay", null, 10, USER_ID);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items())
                .extracting(PaymentCursorRes.PaymentSummaryDto::paymentId)
                .containsExactly(30L, 29L);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("커서가 있으면 full-text 다음 페이지를 조회한다")
    void 커서가_있으면_full_text_다음_페이지를_조회한다() {
        Payment p1 = newPayment(49L, 120L, "payment 49");
        Payment p2 = newPayment(48L, 121L, "payment 48");
        Payment p3 = newPayment(47L, 122L, "payment 47");

        when(paymentJpaRepository.findNextPageIdsByKeywordFullText(eq("+payment*"), eq(50L), eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(49L, 48L, 47L));
        when(paymentJpaRepository.findAllWithInvoiceByIdInOrderByIdDesc(eq(List.of(49L, 48L, 47L))))
                .thenReturn(List.of(p1, p2, p3));

        PaymentCursorRes response = paymentService.search("payment", 50L, 2, USER_ID);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(48L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentJpaRepository).findNextPageIdsByKeywordFullText(
                eq("+payment*"),
                eq(50L),
                eq(USER_ID),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    @DisplayName("검색어가 구분자만 있으면 빈 목록을 반환한다")
    void 검색어가_구분자만_있으면_빈_목록을_반환한다() {
        PaymentCursorRes response = paymentService.search(" !!! ??? ", null, 10, USER_ID);

        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();

        verify(paymentJpaRepository, never()).findFirstPageIdsByKeywordFullText(any(), anyLong(), any(Pageable.class));
        verify(paymentJpaRepository, never()).findNextPageIdsByKeywordFullText(any(), anyLong(), anyLong(), any(Pageable.class));
        verify(paymentJpaRepository, never()).findAllWithInvoiceByIdInOrderByIdDesc(any());
    }

    @Test
    @DisplayName("검색어 길이가 최대를 초과하면 INVALID_SEARCH_KEYWORD_LENGTH 예외가 발생한다")
    void 검색어_길이가_최대를_초과하면_INVALID_SEARCH_KEYWORD_LENGTH_예외가_발생한다() {
        assertThatThrownBy(() -> paymentService.search("aaaaaaaaaaaaaaaaaaaaa", null, 10, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(PaymentErrorCode.INVALID_SEARCH_KEYWORD_LENGTH);
                });
    }

    private Payment newPayment(Long paymentId, Long invoiceId, String name) {
        Invoice invoice = mock(Invoice.class);
        lenient().when(invoice.getId()).thenReturn(invoiceId);
        lenient().when(invoice.getTitle()).thenReturn(name);

        Payment payment = Payment.builder()
                .invoice(invoice)
                .pgProvider(PgProvider.TOSS)
                .method(PaymentMethod.TRANSFER)
                .paymentStatus(PaymentStatus.READY)
                .requestedAmount(new BigDecimal("1000"))
                .requestedAt(LocalDateTime.of(2030, 1, 1, 10, 0))
                .build();
        ReflectionTestUtils.setField(payment, "id", paymentId);

        return payment;
    }
}
