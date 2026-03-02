package org.triple.backend.payment.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.infra.TossPayment;
import org.triple.backend.payment.infra.dto.ConfirmResponse;
import org.triple.backend.payment.repository.PaymentJpaRepository;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final TossPayment tossPayment;
    private final PaymentJpaRepository paymentJpaRepository;

    @Transactional
    public Payment readyToInProgressPayment(PaymentConfirmReq paymentConfirmReq, Long paymentId, Long userId) {
        Payment payment = paymentJpaRepository.findByOrderId(paymentConfirmReq.orderId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT));

        if(!payment.isStatus(PaymentStatus.READY)) {
            throw new BusinessException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        if(!payment.isApprovedAmount(paymentConfirmReq.approvedAmount())) {
            throw new BusinessException(PaymentErrorCode.ILLEGAL_AMOUNT);
        }

        payment.updateStatus(PaymentStatus.IN_PROGRESS);
        return payment;
    }

    public ConfirmResponse confirm(Payment payment) {
        return tossPayment.confirmRequest(payment);
    }

    @Retryable(
        retryFor = {DataAccessException.class},
        noRetryFor = {BusinessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    @Transactional
    public Payment inprogressToDonePayment(ConfirmResponse confirmResponse) {
        Payment payment = paymentJpaRepository.findByOrderId(confirmResponse.orderId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT));

        if (confirmResponse.totalAmount() == null ||
            payment.getApprovedAmount().compareTo(confirmResponse.totalAmount()) != 0) {
            throw new BusinessException(PaymentErrorCode.ILLEGAL_AMOUNT);
        }

        if(!payment.isStatus(PaymentStatus.IN_PROGRESS)) {
            throw new BusinessException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        payment.updateStatus(PaymentStatus.DONE);
        payment.updateReceiptUrl(confirmResponse.receipt().url());

        return payment;
    }

    @Retryable(
        retryFor = {DataAccessException.class},
        noRetryFor = {BusinessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    @Transactional
    public void failConfirm(PaymentConfirmReq paymentConfirmReq, PaymentStatus paymentStatus) {
        Payment payment = paymentJpaRepository.findByOrderId(paymentConfirmReq.orderId())
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT));

        if (!payment.isStatus(PaymentStatus.IN_PROGRESS)) {
            throw new BusinessException(PaymentErrorCode.ALREADY_PROCESSED_PAYMENT);
        }

        payment.updateStatus(paymentStatus);
    }
}
