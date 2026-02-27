package org.triple.backend.invoice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.invoice.entity.Invoice;

import java.util.Optional;

public interface InvoiceJpaRepository extends JpaRepository<Invoice, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :invoiceId")
    Optional<Invoice> findByIdForUpdate(Long invoiceId);
}
