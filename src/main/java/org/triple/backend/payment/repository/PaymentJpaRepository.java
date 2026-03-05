package org.triple.backend.payment.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    boolean existsByInvoiceId(Long invoiceId);

    boolean existsByInvoiceIdAndUserIdAndPaymentStatusIn(
            Long invoiceId,
            Long userId,
            Collection<PaymentStatus> paymentStatuses
    );

    @Query("SELECT p FROM Payment p JOIN FETCH p.invoice WHERE p.user.id = :userId ORDER BY p.id desc")
    List<Payment> findFirstPage(Long userId, Pageable pageable);

    @Query("SELECT p FROM Payment p JOIN FETCH p.invoice WHERE p.user.id = :userId AND p.id < :cursor ORDER BY p.id desc")
    List<Payment> findNextPage(Long userId, Long cursor, Pageable pageable);

    @Query(value = """
            SELECT p.payment_id
            FROM payment p
            JOIN invoice i ON i.invoice_id = p.invoice_id
            WHERE p.user_id = :userId
              AND MATCH(i.title) AGAINST(:booleanQuery IN BOOLEAN MODE)
            ORDER BY p.payment_id DESC
            """, nativeQuery = true)
    List<Long> findFirstPageIdsByKeywordFullText(String booleanQuery, Long userId, Pageable pageable);

    @Query(value = """
            SELECT p.payment_id
            FROM payment p
            JOIN invoice i ON i.invoice_id = p.invoice_id
            WHERE p.payment_id < :cursor
              AND p.user_id = :userId
              AND MATCH(i.title) AGAINST(:booleanQuery IN BOOLEAN MODE)
            ORDER BY p.payment_id DESC
            """, nativeQuery = true)
    List<Long> findNextPageIdsByKeywordFullText(String booleanQuery, Long cursor, Long userId, Pageable pageable);

    @Query("SELECT p FROM Payment p JOIN FETCH p.invoice WHERE p.id IN :paymentIds ORDER BY p.id DESC")
    List<Payment> findAllWithInvoiceByIdInOrderByIdDesc(List<Long> paymentIds);

    @Query("SELECT p FROM Payment p JOIN FETCH p.user WHERE p.invoice.id = :invoiceId ORDER BY p.requestedAt DESC")
    List<Payment> findAllByInvoiceIdWithUser(Long invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT p
    FROM Payment p
    JOIN FETCH p.user u
    WHERE p.orderId = :orderId
    """)
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
