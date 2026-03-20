package org.triple.backend.transfer.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.triple.backend.transfer.entity.TransferStatus;
import org.triple.backend.transfer.entity.TransferUser;

import java.util.Optional;

public interface TransferUserJpaRepository extends JpaRepository<TransferUser, Long> {

    @Modifying(flushAutomatically = true)
    @Query("delete from TransferUser iu where iu.transfer.id = :transferId")
    void deleteAllByTransferIdInBatch(@Param("transferId") Long transferId);

    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "1000"))
    @Query("SELECT iu FROM TransferUser iu JOIN FETCH iu.user u WHERE u.id =:userId AND iu.transfer.id =:transferId AND iu.transfer.transferStatus = :transferStatus")
    Optional<TransferUser> findByUserIdAndTransferIdAndTransferStatusForUpdate(
            Long userId,
            Long transferId,
            TransferStatus transferStatus
    );
}
