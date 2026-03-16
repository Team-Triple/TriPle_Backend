package org.triple.backend.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.triple.backend.payment.entity.outbox.PaymentEvent;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PaymentEventJpaRepository extends JpaRepository<PaymentEvent,Long> {


    @Query(value = """
    SELECT *
    FROM payment_event p
    WHERE p.payment_event_status = 'PENDING'
    ORDER BY p.payment_event_id
    LIMIT :eventCount
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<PaymentEvent> findPendingEventsForUpdate(@Param("eventCount") int eventCount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
    SELECT p
    FROM PaymentEvent p
    WHERE p.paymentEventBody.orderId = :orderId
    """)
    Optional<PaymentEvent> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Query(value = """
    SELECT *
    FROM payment_event p
    WHERE p.payment_event_status = 'RETRYABLE'
    AND p.retry_count <= :maxRetry
    ORDER BY p.retry_count ASC, p.last_event_time ASC
    LIMIT :eventCount
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<PaymentEvent> findFailedEventsForUpdate(@Param("eventCount") int eventCount, @Param("maxRetry") int maxRetry);
}
