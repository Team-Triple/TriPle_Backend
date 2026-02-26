package org.triple.backend.travel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.travel.entity.UserTravelItinerary;

import java.util.Optional;

public interface UserTravelItineraryJpaRepository extends JpaRepository<UserTravelItinerary, Long> {

    Optional<UserTravelItinerary> findByUserIdAndTravelItineraryId(Long userId, Long travelItineraryId);

    boolean existsByUserIdAndTravelItineraryId(Long userId, Long travelItineraryId);
}
