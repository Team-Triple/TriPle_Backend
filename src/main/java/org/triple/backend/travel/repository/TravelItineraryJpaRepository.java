package org.triple.backend.travel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.travel.entity.TravelItinerary;

import java.util.Optional;

public interface TravelItineraryJpaRepository extends JpaRepository<TravelItinerary, Long> {
    Optional<TravelItinerary> findById(Long travelId);
}
