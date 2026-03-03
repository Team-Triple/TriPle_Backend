package org.triple.backend.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;

import java.util.Collection;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    boolean existsByInvoiceId(Long invoiceId);

    boolean existsByInvoiceIdAndUserIdAndPaymentStatusIn(
            Long invoiceId,
            Long userId,
            Collection<PaymentStatus> paymentStatuses
    );
}
