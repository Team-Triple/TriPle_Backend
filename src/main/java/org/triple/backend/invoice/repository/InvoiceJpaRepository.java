package org.triple.backend.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.invoice.entity.Invoice;

public interface InvoiceJpaRepository extends JpaRepository<Invoice, Long> {
}
