package org.triple.backend.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.triple.backend.invoice.entity.InvoiceUser;

public interface InvoiceUserJpaRepository extends JpaRepository<InvoiceUser, Long> {

    @Modifying(flushAutomatically = true)
    @Query("delete from InvoiceUser iu where iu.invoice.id = :invoiceId")
    void deleteAllByInvoiceIdInBatch(@Param("invoiceId") Long invoiceId);
}
