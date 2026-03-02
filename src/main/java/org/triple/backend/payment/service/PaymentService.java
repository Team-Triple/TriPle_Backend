package org.triple.backend.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.invoice.exception.InvoiceErrorCode;
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.payment.dto.request.PaymentCreateReq;
import org.triple.backend.payment.dto.response.PaymentCreateRes;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentMethod;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.entity.PgProvider;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.repository.PaymentJpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final List<PaymentStatus> ACTIVE_STATUSES = List.of(PaymentStatus.READY, PaymentStatus.IN_PROGRESS);

    private final PaymentJpaRepository paymentJpaRepository;
    private final InvoiceUserJpaRepository invoiceUserJpaRepository;
    private final InvoiceJpaRepository invoiceJpaRepository;

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
                dto.name(),
                PaymentMethod.TRANSFER,
                orderId,
                dto.amount()
        );
        try {
            paymentJpaRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(PaymentErrorCode.DUPLICATED_PAYMENT);
        }

        return new PaymentCreateRes(orderId, dto.name(), dto.amount());
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
}
