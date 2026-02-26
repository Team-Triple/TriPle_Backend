package org.triple.backend.travel.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.travel.entity.TravelReviewImage;

import java.util.List;

public interface TravelReviewImageJpaRepository extends JpaRepository<TravelReviewImage, Long> {

    @Query("""
            SELECT tri
            FROM TravelReviewImage tri
            JOIN tri.travelReview tr
            WHERE tr.travelItinerary.group.id = :groupId
              AND tr.isDeleted = false
            ORDER BY tri.id DESC
            """)
    List<TravelReviewImage> findRecentByGroupId(Long groupId, Pageable pageable);
}
