package org.triple.backend.invoice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;

import java.util.Optional;

public interface InvoiceJpaRepository extends JpaRepository<Invoice, Long> {

    @Query("SELECT i FROM Invoice i JOIN FETCH i.travelItinerary JOIN FETCH i.group WHERE i.id = :invoiceId")
    Optional<Invoice> findByIdForUpdateWithGroupAndTravelItinerary(Long invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :invoiceId")
    Optional<Invoice> findByIdForUpdate(Long invoiceId);

    @Query("""
            select distinct i
            from Invoice i
            join fetch i.creator
            left join fetch i.invoiceUsers iu
            left join fetch iu.user
            where i.travelItinerary.id = :travelItineraryId
              and i.invoiceStatus <> :invoiceStatus
            """)
    Optional<Invoice> findInvoiceDetailByTravelItineraryIdAndInvoiceStatusNot(
            Long travelItineraryId,
            InvoiceStatus invoiceStatus
    );
}
