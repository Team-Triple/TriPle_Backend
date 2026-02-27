package org.triple.backend.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.invoice.entity.Invoice;

import java.util.Optional;

public interface InvoiceJpaRepository extends JpaRepository<Invoice, Long> {

    @Query("SELECT i FROM Invoice i JOIN FETCH i.travelItinerary JOIN FETCH i.group WHERE i.id = :invoiceId")
    Optional<Invoice> findById(Long invoiceId);
}
