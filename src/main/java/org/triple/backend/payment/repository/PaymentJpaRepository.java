package org.triple.backend.payment.repository;

import org.springframework.data.domain.Pageable;
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

    @Query("SELECT p FROM Payment p WHERE p.user.id = :userId ORDER BY p.id desc")
    List<Payment> findFirstPage(Long userId, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.user.id = :userId AND p.id < :cursor ORDER BY p.id desc")
    List<Payment> findNextPage(Long userId, Long cursor, Pageable pageable);

    @Query(value = """
            SELECT p.*
            FROM payment p
            WHERE p.user_id = :userId
              AND MATCH(p.name) AGAINST(:booleanQuery IN BOOLEAN MODE)
            ORDER BY p.payment_id DESC
            """, nativeQuery = true)
    List<Payment> findFirstPageByKeywordFullText(String booleanQuery, Long userId, Pageable pageable);

    @Query(value = """
            SELECT p.*
            FROM payment p
            WHERE p.payment_id < :cursor
              AND p.user_id = :userId
              AND MATCH(p.name) AGAINST(:booleanQuery IN BOOLEAN MODE)
            ORDER BY p.payment_id DESC
            """, nativeQuery = true)
    List<Payment> findNextPageByKeywordFullText(String booleanQuery, Long cursor, Long userId, Pageable pageable);
}
