package org.triple.backend.payment.unit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.invoice.exception.InvoiceErrorCode;
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.payment.dto.request.PaymentCreateReq;
import org.triple.backend.payment.dto.response.PaymentCreateRes;
import org.triple.backend.payment.dto.response.PaymentCursorRes;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentMethod;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.entity.PgProvider;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.repository.PaymentJpaRepository;
import org.triple.backend.payment.service.PaymentService;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ServiceTest
@Import(PaymentService.class)
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private InvoiceJpaRepository invoiceJpaRepository;

    @Autowired
    private InvoiceUserJpaRepository invoiceUserJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private TravelItineraryJpaRepository travelItineraryJpaRepository;

    @Autowired
    private UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;

    @Test
    @DisplayName("검색어가 없으면 결제 목록을 커서 기반으로 조회할 수 있다.")
    void 검색어가_없으면_결제_목록을_커서_기반으로_조회할_수_있다() {
        User payer = saveUser("payer-search-browse");
        User other = saveUser("payer-search-other");
        Group group = saveGroup("결제 조회 그룹");
        TravelItinerary firstTravelItinerary = saveTravelItinerary(group, "결제 조회 여행");
        TravelItinerary secondTravelItinerary = saveTravelItinerary(group, "결제 조회 여행2");
        TravelItinerary otherTravelItinerary = saveTravelItinerary(group, "다른 결제 조회 여행");
        Invoice firstInvoice = saveInvoice(group, payer, firstTravelItinerary, InvoiceStatus.CONFIRM, "제주 렌트비");
        Invoice secondInvoice = saveInvoice(group, payer, secondTravelItinerary, InvoiceStatus.CONFIRM, "제주 숙소비");
        Invoice otherInvoice = saveInvoice(group, other, otherTravelItinerary, InvoiceStatus.CONFIRM, "다른 유저 결제");

        paymentJpaRepository.save(
                Payment.builder()
                        .invoice(firstInvoice)
                        .user(payer)
                        .pgProvider(PgProvider.TOSS)
                        .method(PaymentMethod.TRANSFER)
                        .orderId(UUID.randomUUID().toString())
                        .requestedAmount(new BigDecimal("1000"))
                        .paymentStatus(PaymentStatus.READY)
                        .requestedAt(LocalDateTime.of(2030, 3, 20, 10, 0))
                        .build()
        );
        paymentJpaRepository.save(
                Payment.builder()
                        .invoice(secondInvoice)
                        .user(payer)
                        .pgProvider(PgProvider.TOSS)
                        .method(PaymentMethod.TRANSFER)
                        .orderId(UUID.randomUUID().toString())
                        .requestedAmount(new BigDecimal("2000"))
                        .paymentStatus(PaymentStatus.READY)
                        .requestedAt(LocalDateTime.of(2030, 3, 20, 11, 0))
                        .build()
        );
        paymentJpaRepository.save(
                Payment.builder()
                        .invoice(otherInvoice)
                        .user(other)
                        .pgProvider(PgProvider.TOSS)
                        .method(PaymentMethod.TRANSFER)
                        .orderId(UUID.randomUUID().toString())
                        .requestedAmount(new BigDecimal("5000"))
                        .paymentStatus(PaymentStatus.READY)
                        .requestedAt(LocalDateTime.of(2030, 3, 20, 12, 0))
                        .build()
        );

        PaymentCursorRes response = paymentService.search(null, null, 10, payer.getId());

        assertThat(response.items()).hasSize(2);
        assertThat(response.items())
                .extracting(PaymentCursorRes.PaymentSummaryDto::name)
                .contains("제주 렌트비", "제주 숙소비");
        assertThat(response.items())
                .extracting(PaymentCursorRes.PaymentSummaryDto::name)
                .doesNotContain("다른 유저 결제");
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("검색어 길이가 20자를 초과하면 INVALID_SEARCH_KEYWORD_LENGTH 예외가 발생한다.")
    void 검색어_길이가_20자를_초과하면_INVALID_SEARCH_KEYWORD_LENGTH_예외가_발생한다() {
        assertThatThrownBy(() -> paymentService.search("aaaaaaaaaaaaaaaaaaaaa", null, 10, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.INVALID_SEARCH_KEYWORD_LENGTH);
                });
    }

    @Test
    @DisplayName("CONFIRM 청구서의 결제 대상자는 결제 생성 요청을 할 수 있다.")
    void CONFIRM_청구서의_결제_대상자는_결제_생성_요청을_할_수_있다() {
        // given
        User payer = saveUser("payer-success");
        Group group = saveGroup("결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "결제 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "제주 렌트비");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, new BigDecimal("10000")));

        PaymentCreateReq request = new PaymentCreateReq(new BigDecimal("4000"), "제주 렌트비");

        // when
        PaymentCreateRes response = paymentService.create(request, invoice.getId(), payer.getId());

        // then
        assertThat(response.orderId()).isNotBlank();
        assertThat(response.orderName()).isEqualTo("제주 렌트비");
        assertThat(response.amount()).isEqualByComparingTo("4000");

        assertThat(paymentJpaRepository.count()).isEqualTo(1L);
        assertThat(paymentJpaRepository.existsByInvoiceIdAndUserIdAndPaymentStatusIn(
                invoice.getId(),
                payer.getId(),
                List.of(PaymentStatus.READY)
        )).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 청구서로 결제 생성 요청하면 NOT_FOUND_INVOICE 예외가 발생한다.")
    void 존재하지_않는_청구서로_결제_생성_요청하면_NOT_FOUND_INVOICE_예외가_발생한다() {
        PaymentCreateReq request = new PaymentCreateReq(new BigDecimal("1000"), "결제");

        assertThatThrownBy(() -> paymentService.create(request, 999L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(InvoiceErrorCode.NOT_FOUND_INVOICE);
                });
    }

    @Test
    @DisplayName("CONFIRM 상태가 아닌 청구서는 결제를 생성할 수 없다.")
    void CONFIRM_상태가_아닌_청구서는_결제를_생성할_수_없다() {
        // given
        User payer = saveUser("payer-not-allowed");
        Group group = saveGroup("결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "결제 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.UNCONFIRM, "미확정 청구서");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, new BigDecimal("10000")));

        PaymentCreateReq request = new PaymentCreateReq(new BigDecimal("1000"), "미확정 결제");

        // when & then
        assertThatThrownBy(() -> paymentService.create(request, invoice.getId(), payer.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_ALLOWED);
                });
    }

    @Test
    @DisplayName("남은 금액이 0이면 결제 생성 요청 시 PAYMENT_ALREADY_COMPLETED 예외가 발생한다.")
    void 남은_금액이_0이면_결제_생성_요청_시_PAYMENT_ALREADY_COMPLETED_예외가_발생한다() {
        // given
        User payer = saveUser("payer-completed");
        Group group = saveGroup("완납 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "완납 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "완납 청구서");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, BigDecimal.ZERO));

        PaymentCreateReq request = new PaymentCreateReq(new BigDecimal("1000"), "완납 결제 시도");

        // when & then
        assertThatThrownBy(() -> paymentService.create(request, invoice.getId(), payer.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_COMPLETED);
                });
    }

    @Test
    @DisplayName("요청 금액이 남은 금액보다 크면 PAYMENT_AMOUNT_EXCEEDS_REMAINING 예외가 발생한다.")
    void 요청_금액이_남은_금액보다_크면_PAYMENT_AMOUNT_EXCEEDS_REMAINING_예외가_발생한다() {
        // given
        User payer = saveUser("payer-exceeds");
        Group group = saveGroup("초과 결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "초과 결제 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "초과 결제 청구서");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, new BigDecimal("3000")));

        PaymentCreateReq request = new PaymentCreateReq(new BigDecimal("4000"), "초과 결제 시도");

        // when & then
        assertThatThrownBy(() -> paymentService.create(request, invoice.getId(), payer.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING);
                });
    }

    @Test
    @DisplayName("이미 진행 중인 결제가 있으면 PAYMENT_ALREADY_IS_ACTIVE 예외가 발생한다.")
    void 이미_진행_중인_결제가_있으면_PAYMENT_ALREADY_IS_ACTIVE_예외가_발생한다() {
        // given
        User payer = saveUser("payer-active");
        Group group = saveGroup("진행중 결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "진행중 결제 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "진행중 결제 청구서");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, new BigDecimal("10000")));
        paymentJpaRepository.save(
                Payment.builder()
                        .invoice(invoice)
                        .user(payer)
                        .pgProvider(PgProvider.TOSS)
                        .method(PaymentMethod.TRANSFER)
                        .orderId(UUID.randomUUID().toString())
                        .requestedAmount(new BigDecimal("1000"))
                        .paymentStatus(PaymentStatus.READY)
                        .requestedAt(LocalDateTime.now())
                        .build()
        );

        PaymentCreateReq request = new PaymentCreateReq(new BigDecimal("2000"), "중복 결제 시도");

        // when & then
        assertThatThrownBy(() -> paymentService.create(request, invoice.getId(), payer.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_ALREADY_IS_ACTIVE);
                });
    }

    @Test
    @DisplayName("여행 멤버는 invoiceId로 결제 목록을 조회할 수 있다.")
    void travelMemberCanSearchPaymentsByInvoiceId() {
        User viewer = saveUser("viewer-search-success");
        User payer = saveUser("payer-search-success");
        Group group = saveGroup("search-group");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "search-travel");
        saveTravelMembership(viewer, travelItinerary, UserRole.MEMBER);
        saveTravelMembership(payer, travelItinerary, UserRole.MEMBER);
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "search-invoice");

        Payment oldPayment = paymentJpaRepository.save(
                Payment.builder()
                        .invoice(invoice)
                        .user(payer)
                        .pgProvider(PgProvider.TOSS)
                        .method(PaymentMethod.TRANSFER)
                        .orderId("order-old")
                        .requestedAmount(new BigDecimal("1000"))
                        .paymentStatus(PaymentStatus.READY)
                        .requestedAt(LocalDateTime.of(2030, 3, 1, 9, 0))
                        .build()
        );
        Payment latestPayment = paymentJpaRepository.save(
                Payment.builder()
                        .invoice(invoice)
                        .user(payer)
                        .pgProvider(PgProvider.TOSS)
                        .method(PaymentMethod.TRANSFER)
                        .orderId("order-latest")
                        .requestedAmount(new BigDecimal("2000"))
                        .paymentStatus(PaymentStatus.IN_PROGRESS)
                        .requestedAt(LocalDateTime.of(2030, 3, 1, 10, 0))
                        .build()
        );

        PaymentSearchRes response = paymentService.search(invoice.getId(), viewer.getId());

        assertThat(response.invoiceId()).isEqualTo(invoice.getId());
        assertThat(response.payments()).hasSize(2);
        assertThat(response.payments().get(0).orderId()).isEqualTo("order-latest");
        assertThat(response.payments().get(0).requestedAmount()).isEqualByComparingTo("2000");
        assertThat(response.payments().get(0).paymentStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("존재하지 않는 invoiceId로 결제 조회 시 NOT_FOUND_INVOICE 예외가 발생한다.")
    void searchPaymentsWithUnknownInvoiceIdThrowsNotFoundInvoice() {
        User viewer = saveUser("viewer-search-not-found");

        assertThatThrownBy(() -> paymentService.search(999L, viewer.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(InvoiceErrorCode.NOT_FOUND_INVOICE);
                });
    }

    @Test
    @DisplayName("여행 멤버가 아니면 결제 목록 조회 시 PAYMENT_SEARCH_NOT_ALLOWED 예외가 발생한다.")
    void nonTravelMemberCannotSearchPayments() {
        User member = saveUser("member-search");
        User outsider = saveUser("outsider-search");
        Group group = saveGroup("search-permission-group");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "search-permission-travel");
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);
        Invoice invoice = saveInvoice(group, member, travelItinerary, InvoiceStatus.CONFIRM, "search-permission-invoice");

        assertThatThrownBy(() -> paymentService.search(invoice.getId(), outsider.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_SEARCH_NOT_ALLOWED);
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동일 사용자의 동일 청구서 결제 생성 요청이 동시에 들어오면 하나만 성공한다.")
    void 동일_사용자의_동일_청구서_결제_생성_요청이_동시에_들어오면_하나만_성공한다() throws InterruptedException {
        User payer = saveUser("payer-concurrency");
        Group group = saveGroup("동시성 결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "동시성 결제 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "동시성 결제 청구서");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, new BigDecimal("10000")));

        PaymentCreateReq request = new PaymentCreateReq(new BigDecimal("1000"), "동시성 결제");

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable task = () -> {
            ready.countDown();
            try {
                start.await();
                paymentService.create(request, invoice.getId(), payer.getId());
                successCount.incrementAndGet();
            } catch (Throwable throwable) {
                failures.add(throwable);
            } finally {
                done.countDown();
            }
        };

        try {
            executorService.submit(task);
            executorService.submit(task);

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

            long activePaymentConflictCount = failures.stream()
                    .filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .filter(e -> e.getErrorCode() == PaymentErrorCode.PAYMENT_ALREADY_IS_ACTIVE)
                    .count();

            long lockFailureCount = failures.stream()
                    .filter(e -> e instanceof CannotAcquireLockException || e instanceof PessimisticLockingFailureException)
                    .count();

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failures).hasSize(1);
            assertThat(activePaymentConflictCount + lockFailureCount).isEqualTo(1);
            assertThat(paymentJpaRepository.count()).isEqualTo(1L);
            assertThat(paymentJpaRepository.existsByInvoiceIdAndUserIdAndPaymentStatusIn(
                    invoice.getId(),
                    payer.getId(),
                    List.of(PaymentStatus.READY, PaymentStatus.IN_PROGRESS)
            )).isTrue();
        } finally {
            executorService.shutdownNow();
            paymentJpaRepository.deleteAllInBatch();
            invoiceUserJpaRepository.deleteAllInBatch();
            invoiceJpaRepository.deleteAllInBatch();
            travelItineraryJpaRepository.deleteAllInBatch();
            groupJpaRepository.deleteAllInBatch();
            userJpaRepository.deleteAllInBatch();
        }
    }

    private User saveUser(final String providerId) {
        return userJpaRepository.save(
                User.builder()
                        .providerId(providerId)
                        .nickname(providerId + "-nick")
                        .email(providerId + "@test.com")
                        .profileUrl("http://img")
                        .build()
        );
    }

    private Group saveGroup(final String name) {
        return groupJpaRepository.save(
                Group.create(
                        GroupKind.PUBLIC,
                        name,
                        "설명",
                        "http://thumb",
                        10
                )
        );
    }

    private TravelItinerary saveTravelItinerary(final Group group, final String title) {
        return travelItineraryJpaRepository.save(
                new TravelItinerary(
                        title,
                        LocalDateTime.of(2030, 3, 20, 9, 0),
                        LocalDateTime.of(2030, 3, 22, 18, 0),
                        group,
                        "여행 설명",
                        "http://travel-thumb",
                        5,
                        1,
                        false
                )
        );
    }

    private UserTravelItinerary saveTravelMembership(
            final User user,
            final TravelItinerary travelItinerary,
            final UserRole userRole
    ) {
        return userTravelItineraryJpaRepository.save(UserTravelItinerary.of(user, travelItinerary, userRole));
    }

    private Invoice saveInvoice(
            final Group group,
            final User creator,
            final TravelItinerary travelItinerary,
            final InvoiceStatus invoiceStatus,
            final String title
    ) {
        return invoiceJpaRepository.save(
                Invoice.builder()
                        .group(group)
                        .creator(creator)
                        .travelItinerary(travelItinerary)
                        .invoiceStatus(invoiceStatus)
                        .title(title)
                        .description("청구서 설명")
                        .totalAmount(new BigDecimal("10000"))
                        .dueAt(LocalDateTime.of(2030, 3, 31, 18, 0))
                        .build()
        );
    }
}
