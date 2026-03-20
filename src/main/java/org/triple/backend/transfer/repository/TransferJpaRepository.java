package org.triple.backend.transfer.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.triple.backend.transfer.entity.Transfer;
import org.triple.backend.transfer.entity.TransferStatus;

import java.util.Optional;

public interface TransferJpaRepository extends JpaRepository<Transfer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Transfer i JOIN FETCH i.travelItinerary JOIN FETCH i.group WHERE i.id = :transferId")
    Optional<Transfer> findByIdForUpdateWithGroupAndTravelItinerary(Long transferId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "1000"))
    @Query("select i from Transfer i where i.id = :transferId")
    Optional<Transfer> findByIdForUpdate(Long transferId);

    @Query("""
            select distinct i
            from Transfer i
            join fetch i.creator
            left join fetch i.transferUsers iu
            left join fetch iu.user
            where i.travelItinerary.id = :travelItineraryId
              and i.transferStatus <> :transferStatus
            """)
    Optional<Transfer> findTransferDetailByTravelItineraryIdAndTransferStatusNot(
            Long travelItineraryId,
            TransferStatus transferStatus
    );

    @Query("""
           SELECT i
           FROM Transfer i
           JOIN FETCH i.travelItinerary
           WHERE i.id = :transferId
           """)
    Optional<Transfer> findByIdWithTravelItinerary(Long transferId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "1000"))
    @Query("""
           SELECT i
           FROM Transfer i
           LEFT JOIN FETCH i.transferUsers tu
           LEFT JOIN FETCH tu.user
           WHERE i.id = :transferId
           """)
    Optional<Transfer> findByIdForUpdateWithTransferUsers(Long transferId);
}
