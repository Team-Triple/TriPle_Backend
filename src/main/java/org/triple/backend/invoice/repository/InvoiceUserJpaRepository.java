package org.triple.backend.invoice.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.entity.InvoiceUser;

import java.util.Optional;

public interface InvoiceUserJpaRepository extends JpaRepository<InvoiceUser, Long> {

    @Modifying(flushAutomatically = true)
    @Query("delete from InvoiceUser iu where iu.invoice.id = :invoiceId")
    void deleteAllByInvoiceIdInBatch(@Param("invoiceId") Long invoiceId);

    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "1000"))
    @Query("SELECT iu FROM InvoiceUser iu JOIN FETCH iu.user u WHERE u.id =:userId AND iu.invoice.id =:invoiceId AND iu.invoice.invoiceStatus = :invoiceStatus")
    Optional<InvoiceUser> findByUserIdAndInvoiceIdAndInvoiceStatusForUpdate(
            Long userId,
            Long invoiceId,
            InvoiceStatus invoiceStatus
    );
}
