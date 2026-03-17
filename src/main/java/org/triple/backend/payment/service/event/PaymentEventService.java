package org.triple.backend.payment.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.payment.config.PaymentEventProperties;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.entity.outbox.Error;
import org.triple.backend.payment.entity.outbox.PaymentEvent;
import org.triple.backend.payment.entity.outbox.PaymentEventBody;
import org.triple.backend.payment.entity.outbox.PaymentEventStatus;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.infra.dto.response.PaymentEventFailRes;
import org.triple.backend.payment.infra.dto.response.PaymentEventRes;
import org.triple.backend.payment.infra.dto.response.PaymentEventSuccessRes;
import org.triple.backend.payment.repository.PaymentEventJpaRepository;
import org.triple.backend.payment.repository.PaymentJpaRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventService {
    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentEventJpaRepository paymentEventJpaRepository;
    private final InvoiceUserJpaRepository invoiceUserJpaRepository;
    private final PaymentEventProperties paymentEventProperties;

    @Transactional
    public List<PaymentEventBody> findPendingEvents(int eventCount) {
        List<PaymentEvent> paymentEvents = paymentEventJpaRepository.findPendingEventsForUpdate(eventCount);

        return paymentEvents.stream()
                .peek(paymentEvent -> paymentEvent.updatePaymentEventStatus(PaymentEventStatus.IN_PROGRESS))
                .map(PaymentEvent::getPaymentEventBody)
                .toList();
    }

    @Transactional
    public void applyPaymentEventRes(PaymentEventRes paymentEventRes) {
        if(paymentEventRes instanceof PaymentEventSuccessRes paymentEventSuccess) {
            applyPaymentEventSuccess(paymentEventSuccess);
        } else if(paymentEventRes instanceof PaymentEventFailRes paymentEventFailRes) {
            applyPaymentEventFailed(paymentEventFailRes);
        }
    }

    @Transactional
    public List<PaymentEventBody> findRetryableEvents(int eventCount) {
        List<PaymentEvent> paymentEvents = paymentEventJpaRepository.findFailedEventsForUpdate(eventCount, paymentEventProperties.maxRetryCount());

        return paymentEvents.stream()
                .peek(paymentEvent -> paymentEvent.updatePaymentEventStatus(PaymentEventStatus.IN_PROGRESS))
                .map(PaymentEvent::getPaymentEventBody)
                .toList();
    }

    private void applyPaymentEventSuccess(PaymentEventSuccessRes paymentEventSuccess) {
        Payment payment = paymentJpaRepository.findByOrderIdForUpdate(paymentEventSuccess.orderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT));
        payment.confirm(paymentEventSuccess);

        PaymentEvent paymentEvent = paymentEventJpaRepository.findByOrderIdForUpdate(paymentEventSuccess.orderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT_EVENT));
        paymentEvent.markSuccess();

        InvoiceUser invoiceUser = invoiceUserJpaRepository.findByUserIdAndInvoiceIdAndInvoiceStatusForUpdate(payment.getUser().getId(), payment.getInvoice().getId(), InvoiceStatus.CONFIRM)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_ALLOWED));
        invoiceUser.decreaseRemainAmount(paymentEventSuccess.totalAmount());
    }

    private void applyPaymentEventFailed(PaymentEventFailRes paymentEventFailRes) {
        Payment payment = paymentJpaRepository.findByOrderIdForUpdate(paymentEventFailRes.orderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT));

        PaymentEvent paymentEvent = paymentEventJpaRepository.findByOrderIdForUpdate(paymentEventFailRes.orderId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND_PAYMENT_EVENT));

        Error error = paymentEventFailRes.error();

        if (isRetryableError(error) && !paymentEvent.isRetryCountExceeded(paymentEventProperties.maxRetryCount() - 1)) {
            paymentEvent.markRetryable(error, LocalDateTime.now());
            return;
        }

        if (isRetryableError(error) && paymentEvent.isRetryCountExceeded(paymentEventProperties.maxRetryCount() - 1)) {
            paymentEvent.markDead(error, LocalDateTime.now());
            payment.processPaymentEvent(payment.getPaymentKey(), PaymentStatus.FAILED);
            return;
        }

        paymentEvent.markFailed(error, LocalDateTime.now());
        payment.processPaymentEvent(payment.getPaymentKey(), PaymentStatus.FAILED);
    }

    private boolean isRetryableError(Error error) {
        return error == Error.NETWORK_TIMEOUT
                || error == Error.UPSTREAM_429
                || error == Error.UPSTREAM_5XX;
    }

    @Transactional
    public void finalizeException(String orderId) {
        try {
            PaymentEvent event = paymentEventJpaRepository.findByOrderIdForUpdate(orderId).orElse(null);
            if (event == null) {
                log.error("finalizeException: 결제 이벤트가 발견되지 않았습니다.. orderId={}", orderId);
                return;
            }
            if (event.getPaymentEventStatus() != PaymentEventStatus.IN_PROGRESS) {
                return;
            }

            Payment payment = paymentJpaRepository.findByOrderIdForUpdate(orderId).orElse(null);

            if (payment == null) {
                event.markDead(Error.UNKNOWN, LocalDateTime.now());
                log.error("finalizeException: 결제가 발견되지 않았습니다. DEAD 상태가 됩니다. orderId={}", orderId);
                return;
            }

            if (event.isRetryCountExceeded(paymentEventProperties.maxRetryCount() - 1)) {
                event.markDead(Error.UNKNOWN, LocalDateTime.now());
                payment.processPaymentEvent(payment.getPaymentKey(), PaymentStatus.FAILED);
                return;
            }

            event.markRetryable(Error.UNKNOWN, LocalDateTime.now());
        } catch (Exception e) {
            log.error("finalizeException failed. orderId={}", orderId, e);
        }
    }
}
