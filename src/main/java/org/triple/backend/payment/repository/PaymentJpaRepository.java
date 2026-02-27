package org.triple.backend.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.payment.entity.Payment;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    boolean existsByInvoiceId(Long invoiceId);
}
