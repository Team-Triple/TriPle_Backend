package org.triple.backend.travel.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.triple.backend.travel.entity.TravelItinerary;

import java.util.List;
import java.util.Optional;

public interface TravelItineraryJpaRepository extends JpaRepository<TravelItinerary, Long> {
    Optional<TravelItinerary> findById(Long travelId);

    long countByGroupIdAndIsDeletedFalse(Long groupId);

    @Query("select t from TravelItinerary t where t.id = :travelId and t.isDeleted = false")
    Optional<TravelItinerary> findByIdAndIsDeletedFalse(@Param("travelId") Long travelId);

    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t from TravelItinerary t WHERE t.id = :travelItineraryId AND t.group.id = :groupId AND t.isDeleted = false")
    Optional<TravelItinerary> findByIdAndGroupIdAndIsDeletedFalseForUpdate(Long travelItineraryId, Long groupId);

    @Query("""
        SELECT t FROM TravelItinerary t
        WHERE t.group.id = :groupId
            AND t.isDeleted = false
        ORDER BY t.id DESC
    """)
    List<TravelItinerary> findGroupTravelsFirstPage(@Param("groupId") Long groupId, Pageable pageable);

    @Query("""
        SELECT t FROM TravelItinerary t
        WHERE t.group.id = :groupId
            AND t.isDeleted = false
            AND t.id < :cursor
        ORDER BY t.id DESC
    """)
    List<TravelItinerary> findGroupTravelsNextPage(
            @Param("groupId") Long groupId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TravelItinerary t where t.id = :travelId and t.isDeleted = false")
    Optional<TravelItinerary> findByIdAndIsDeletedFalseForUpdate(Long travelId);

    @Query("""
            SELECT t
            FROM TravelItinerary t
            WHERE t.group.id = :groupId
              AND t.isDeleted = false
            ORDER BY t.id desc
            """)
    List<TravelItinerary> findRecentByGroupId(Long groupId, Pageable pageable);
}
