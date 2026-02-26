package org.triple.backend.travel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.triple.backend.travel.entity.UserTravelItinerary;

import java.util.Optional;

public interface UserTravelItineraryJpaRepository extends JpaRepository<UserTravelItinerary, Long> {

    @Query("""
            SELECT ut
            FROM UserTravelItinerary ut
            JOIN FETCH ut.travelItinerary ti
            WHERE ut.user.id = :userId
            AND ti.id = :travelItineraryId
            """)
    Optional<UserTravelItinerary> findByUserIdAndTravelItineraryId(
            @Param("userId") Long userId, @Param("travelItineraryId") Long travelItineraryId
    );

    boolean existsByUserIdAndTravelItineraryId(Long userId, Long travelItineraryId);
}
