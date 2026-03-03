package org.triple.backend.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;

import java.util.Collection;
import java.util.List;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    boolean existsByInvoiceId(Long invoiceId);

    boolean existsByInvoiceIdAndUserIdAndPaymentStatusIn(
            Long invoiceId,
            Long userId,
            Collection<PaymentStatus> paymentStatuses
    );

    @Query("""
            select p
            from Payment p
            join fetch p.user
            where p.invoice.id = :invoiceId
            order by p.requestedAt desc, p.id desc
            """)
    List<Payment> findAllByInvoiceIdWithUser(Long invoiceId);
}
