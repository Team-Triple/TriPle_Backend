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
                invoiceUserJpaRepository,
                invoiceJpaRepository,
                userTravelItineraryJpaRepository
        );
    }

    @Test
    @DisplayName("검색어가 없으면 전체 결제 목록 browse 첫 페이지를 조회한다")
    void 검색어가_없으면_전체_결제_목록_browse_첫_페이지를_조회한다() {
        Payment p1 = newPayment(30L, 100L, "제주 렌트비");
        Payment p2 = newPayment(29L, 101L, "제주 숙소비");

        when(paymentJpaRepository.findFirstPage(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(p1, p2));

        PaymentCursorRes response = paymentService.search(null, null, 10, USER_ID);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        verify(paymentJpaRepository).findFirstPage(eq(USER_ID), any(Pageable.class));
        verify(paymentJpaRepository, never()).findFirstPageIdsByKeywordFullText(any(), anyLong(), any(Pageable.class));
        verify(paymentJpaRepository, never()).findAllWithInvoiceByIdInOrderByIdDesc(any());
    }

    @Test
    @DisplayName("검색어가 없고 커서가 있으면 전체 결제 목록 browse 다음 페이지를 조회한다")
    void 검색어가_없고_커서가_있으면_전체_결제_목록_browse_다음_페이지를_조회한다() {
        Payment p1 = newPayment(49L, 120L, "결제49");
        Payment p2 = newPayment(48L, 121L, "결제48");
        Payment p3 = newPayment(47L, 122L, "결제47");

        when(paymentJpaRepository.findNextPage(eq(USER_ID), eq(50L), any(Pageable.class)))
                .thenReturn(List.of(p1, p2, p3));

        PaymentCursorRes response = paymentService.search(" ", 50L, 2, USER_ID);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(48L);
        verify(paymentJpaRepository).findNextPage(eq(USER_ID), eq(50L), any(Pageable.class));
        verify(paymentJpaRepository, never()).findNextPageIdsByKeywordFullText(any(), anyLong(), anyLong(), any(Pageable.class));
        verify(paymentJpaRepository, never()).findAllWithInvoiceByIdInOrderByIdDesc(any());
    }

    @Test
    @DisplayName("FULLTEXT 검색은 키워드를 boolean mode 쿼리로 변환해 첫 페이지를 조회한다")
    void FULLTEXT_검색은_키워드를_boolean_mode_쿼리로_변환해_첫_페이지를_조회한다() {
        Payment p1 = newPayment(30L, 100L, "제주 렌트비");
        Payment p2 = newPayment(29L, 101L, "제주 숙소비");

        when(paymentJpaRepository.findFirstPageIdsByKeywordFullText(eq("+제주* +결제*"), eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(30L, 29L));
        when(paymentJpaRepository.findAllWithInvoiceByIdInOrderByIdDesc(eq(List.of(30L, 29L))))
                .thenReturn(List.of(p1, p2));

        PaymentCursorRes response = paymentService.search(" 제주!! 결제? ", null, 10, USER_ID);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.items())
                .extracting(PaymentCursorRes.PaymentSummaryDto::name)
                .containsExactly("제주 렌트비", "제주 숙소비");
    }

    @Test
    @DisplayName("FULLTEXT 검색은 구두점을 단어 경계로 처리해 boolean mode 쿼리로 변환한다")
    void FULLTEXT_검색은_구두점을_단어_경계로_처리해_boolean_mode_쿼리로_변환한다() {
        Payment p1 = newPayment(31L, 102L, "jeju-transfer plan");

        when(paymentJpaRepository.findFirstPageIdsByKeywordFullText(eq("+jeju* +transfer* +plan*"), eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(31L));
        when(paymentJpaRepository.findAllWithInvoiceByIdInOrderByIdDesc(eq(List.of(31L))))
                .thenReturn(List.of(p1));

        PaymentCursorRes response = paymentService.search("jeju-transfer, plan", null, 10, USER_ID);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).name()).isEqualTo("jeju-transfer plan");
    }

    @Test
    @DisplayName("FULLTEXT 검색 다음 페이지는 커서 조건과 pageSize+1로 조회하고 hasNext를 계산한다")
    void FULLTEXT_검색_다음_페이지는_커서_조건과_pageSize_플러스_일로_조회하고_hasNext를_계산한다() {
        Payment p1 = newPayment(49L, 120L, "결제49");
        Payment p2 = newPayment(48L, 121L, "결제48");
        Payment p3 = newPayment(47L, 122L, "결제47");

        when(paymentJpaRepository.findNextPageIdsByKeywordFullText(eq("+제주* +결제*"), eq(50L), eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(49L, 48L, 47L));
        when(paymentJpaRepository.findAllWithInvoiceByIdInOrderByIdDesc(eq(List.of(49L, 48L, 47L))))
                .thenReturn(List.of(p1, p2, p3));

        PaymentCursorRes response = paymentService.search("제주 결제", 50L, 2, USER_ID);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(48L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentJpaRepository).findNextPageIdsByKeywordFullText(eq("+제주* +결제*"), eq(50L), eq(USER_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    @DisplayName("FULLTEXT 검색어가 특수문자만 있으면 빈 결과를 반환한다")
    void FULLTEXT_검색어가_특수문자만_있으면_빈_결과를_반환한다() {
        PaymentCursorRes response = paymentService.search(" !!! ??? ", null, 10, USER_ID);

        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();

        verify(paymentJpaRepository, never()).findFirstPageIdsByKeywordFullText(any(), anyLong(), any(Pageable.class));
        verify(paymentJpaRepository, never()).findNextPageIdsByKeywordFullText(any(), anyLong(), anyLong(), any(Pageable.class));
        verify(paymentJpaRepository, never()).findAllWithInvoiceByIdInOrderByIdDesc(any());
    }

    @Test
    @DisplayName("검색어 길이가 20자를 초과하면 INVALID_SEARCH_KEYWORD_LENGTH 예외가 발생한다")
    void 검색어_길이가_20자를_초과하면_INVALID_SEARCH_KEYWORD_LENGTH_예외가_발생한다() {
        assertThatThrownBy(() -> paymentService.search("aaaaaaaaaaaaaaaaaaaaa", null, 10, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.INVALID_SEARCH_KEYWORD_LENGTH);
                });

        verify(paymentJpaRepository, never()).findFirstPageIdsByKeywordFullText(any(), anyLong(), any(Pageable.class));
        verify(paymentJpaRepository, never()).findNextPageIdsByKeywordFullText(any(), anyLong(), anyLong(), any(Pageable.class));
        verify(paymentJpaRepository, never()).findAllWithInvoiceByIdInOrderByIdDesc(any());
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
