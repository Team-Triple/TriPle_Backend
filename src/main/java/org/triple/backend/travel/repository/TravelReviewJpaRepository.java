package org.triple.backend.travel.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.travel.entity.TravelReview;

import java.util.List;

public interface TravelReviewJpaRepository extends JpaRepository<TravelReview, Long> {

    @Query("""
            SELECT tr
            FROM TravelReview tr
            JOIN FETCH tr.user u
            JOIN FETCH tr.travelItinerary ti
            WHERE ti.group.id = :groupId
              AND tr.isDeleted = false
            ORDER BY tr.id DESC
            """)
    List<TravelReview> findRecentByGroupId(Long groupId, Pageable pageable);
}
