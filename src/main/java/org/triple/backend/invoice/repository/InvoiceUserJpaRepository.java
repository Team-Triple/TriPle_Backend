package org.triple.backend.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.invoice.entity.InvoiceUser;

public interface InvoiceUserJpaRepository extends JpaRepository<InvoiceUser, Long> {
}
