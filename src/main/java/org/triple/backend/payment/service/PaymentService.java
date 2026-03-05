package org.triple.backend.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.invoice.exception.InvoiceErrorCode;
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.dto.request.PaymentCreateReq;
import org.triple.backend.payment.dto.response.PaymentCreateRes;
import org.triple.backend.payment.dto.response.PaymentCursorRes;
import org.triple.backend.payment.dto.response.PaymentSearchRes;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentMethod;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.entity.PgProvider;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.infra.TossPayment;
import org.triple.backend.payment.infra.dto.response.ConfirmResponse;
import org.triple.backend.payment.repository.PaymentJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.exception.UserErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final List<PaymentStatus> ACTIVE_STATUSES = List.of(PaymentStatus.READY, PaymentStatus.IN_PROGRESS);
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 10;
    private static final int KEYWORD_MAX_LENGTH = 20;

    private final TossPayment tossPayment;
    private final PaymentJpaRepository paymentJpaRepository;
    private final InvoiceUserJpaRepository invoiceUserJpaRepository;
    private final InvoiceJpaRepository invoiceJpaRepository;
    private final UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;

    @Transactional(timeout = 3)
    public PaymentCreateRes create(final PaymentCreateReq dto, final Long invoiceId, final Long userId) {

        Invoice invoice = invoiceJpaRepository.findByIdForUpdate(invoiceId).orElseThrow(() -> new BusinessException(InvoiceErrorCode.NOT_FOUND_INVOICE));

        InvoiceUser invoiceUser = invoiceUserJpaRepository.findByUserIdAndInvoiceIdAndInvoiceStatusForUpdate(userId, invoiceId, InvoiceStatus.CONFIRM)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_ALLOWED));

        validatePaymentAmountOrThrow(dto.amount(), invoiceUser.getRemainAmount());
        validateNoActivePaymentOrThrow(invoiceId, userId);

        String orderId = getOrderId();
        Payment payment = Payment.create(
                invoice,
                invoiceUser.getUser(),
                PgProvider.TOSS,
                PaymentMethod.TRANSFER,
                orderId,
                dto.amount()
        );
        try {
            paymentJpaRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(PaymentErrorCode.DUPLICATED_PAYMENT);
        }

        return new PaymentCreateRes(orderId, invoice.getTitle(), dto.amount());
    }

    @Transactional
    public Payment readyToInProgressPayment(PaymentConfirmReq paymentConfirmReq, Long paymentId, Long userId) {
        Payment payment = paymentJpaRepository.findByOrderIdForUpdate(paymentConfirmReq.orderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT));

        if(payment.getUser() == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if(!payment.getUser().getId().equals(userId)) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONFIRM_NOT_ALLOWED);
        }

        if(!payment.isStatus(PaymentStatus.READY)) {
            throw new BusinessException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        if(!payment.isRequestedAmount(paymentConfirmReq.requestedAmount())) {
            throw new BusinessException(PaymentErrorCode.ILLEGAL_AMOUNT);
        }

        payment.updateStatus(PaymentStatus.IN_PROGRESS);
        return payment;
    }

    public ConfirmResponse confirm(Payment payment) {
        return tossPayment.confirmRequest(payment);
    }

    @Retryable(
            retryFor = {
                    CannotAcquireLockException.class,
                    QueryTimeoutException.class
            },
            noRetryFor = {
                    BusinessException.class,
                    DataIntegrityViolationException.class,
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2, random = true)
    )
    @Transactional
    public Payment inprogressToDonePayment(ConfirmResponse confirmResponse) {
        Payment payment = paymentJpaRepository.findByOrderIdForUpdate(confirmResponse.orderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT));

        if (confirmResponse.totalAmount() == null ||
                payment.getRequestedAmount().compareTo(confirmResponse.totalAmount()) != 0) {
            throw new BusinessException(PaymentErrorCode.ILLEGAL_AMOUNT);
        }

        if(!payment.isStatus(PaymentStatus.IN_PROGRESS)) {
            throw new BusinessException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        payment.confirm(
                confirmResponse.paymentKey(),
                confirmResponse.totalAmount(),
                PaymentStatus.DONE,
                LocalDateTime.now(),    //clock으로 리팩토링 예정
                confirmResponse.receipt().url()
        );

        return payment;
    }

    @Retryable(
            retryFor = {
                    CannotAcquireLockException.class,
                    QueryTimeoutException.class
            },
            noRetryFor = {
                    BusinessException.class,
                    DataIntegrityViolationException.class,
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2, random = true)
    )
    @Transactional
    public void failConfirm(PaymentConfirmReq paymentConfirmReq, PaymentStatus paymentStatus) {
        Payment payment = paymentJpaRepository.findByOrderIdForUpdate(paymentConfirmReq.orderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT));

        if (!payment.isStatus(PaymentStatus.IN_PROGRESS)) {
            throw new BusinessException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        payment.updateStatus(paymentStatus);
    }

    @Transactional(readOnly = true)
    public PaymentCursorRes search(final String keyword, final Long cursor, final int size, final Long userId) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        int pageSize = normalizePageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        if (normalizedKeyword.isBlank()) {
            return browsePayment(cursor, userId, pageable, pageSize);
        }

        if (normalizedKeyword.length() > KEYWORD_MAX_LENGTH) {
            throw new BusinessException(PaymentErrorCode.INVALID_SEARCH_KEYWORD_LENGTH);
        }

        List<Payment> rows = findPageByKeyword(
                normalizedKeyword,
                cursor,
                userId,
                pageable
        );

        return toCursorResponse(rows, pageSize);
    }

    private PaymentCursorRes browsePayment(
            final Long cursor,
            final Long userId,
            final Pageable pageable,
            final int pageSize
    ) {
        List<Payment> rows = (cursor == null)
                ? paymentJpaRepository.findFirstPage(userId, pageable)
                : paymentJpaRepository.findNextPage(userId, cursor, pageable);

        return toCursorResponse(rows, pageSize);
    }

    private int normalizePageSize(int size) {
        return Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
    }

    private List<Payment> findPageByKeyword(final String keyword, final Long cursor, final Long userId, final Pageable pageable) {
        String booleanQuery = toBooleanModeQuery(keyword);
        if (booleanQuery.isBlank()) {
            return List.of();
        }

        List<Long> paymentIds = cursor == null
                ? paymentJpaRepository.findFirstPageIdsByKeywordFullText(booleanQuery, userId, pageable)
                : paymentJpaRepository.findNextPageIdsByKeywordFullText(booleanQuery, cursor, userId, pageable);
        if (paymentIds.isEmpty()) {
            return List.of();
        }

        return paymentJpaRepository.findAllWithInvoiceByIdInOrderByIdDesc(paymentIds);
    }

    private String toBooleanModeQuery(String keyword) {
        return Arrays.stream(keyword.trim().split("[^\\p{L}\\p{N}]+"))
                .filter(token -> !token.isBlank())
                .map(token -> "+" + token + "*")
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    @Transactional(readOnly = true)
    public PaymentSearchRes search(final Long invoiceId, final Long userId) {
        Invoice invoice = invoiceJpaRepository.findByIdWithTravelItinerary(invoiceId)
                .orElseThrow(() -> new BusinessException(InvoiceErrorCode.NOT_FOUND_INVOICE));

        if (!userTravelItineraryJpaRepository.existsByUserIdAndTravelItineraryId(userId, invoice.getTravelItinerary().getId())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_SEARCH_NOT_ALLOWED);
        }

        List<Payment> payments = paymentJpaRepository.findAllByInvoiceIdWithUser(invoiceId);
        return PaymentSearchRes.from(invoiceId, payments);
    }

    private void validateNoActivePaymentOrThrow(final Long invoiceId, final Long userId) {
        if (paymentJpaRepository.existsByInvoiceIdAndUserIdAndPaymentStatusIn(invoiceId, userId, ACTIVE_STATUSES)) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_IS_ACTIVE);
        }
    }

    private void validatePaymentAmountOrThrow(final BigDecimal requestAmount, final BigDecimal remainAmount) {
        if (remainAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_COMPLETED);
        }

        if (requestAmount.compareTo(remainAmount) > 0) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_REMAINING);
        }
    }

    private String getOrderId() {
        return UUID.randomUUID().toString();
    }

    private PaymentCursorRes toCursorResponse(List<Payment> rows, int pageSize) {
        boolean hasNext = rows.size() > pageSize;
        if (hasNext) {
            rows = rows.subList(0, pageSize);
        }

        Long nextCursor = hasNext ? rows.get(rows.size() - 1).getId() : null;
        return PaymentCursorRes.from(rows, nextCursor, hasNext);
    }
}
