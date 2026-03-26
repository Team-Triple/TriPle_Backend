package org.triple.backend.travel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.travel.entity.TravelDoc;

import java.util.Optional;

public interface TravelDocJpaRepository extends JpaRepository<TravelDoc, Long> {
    Optional<TravelDoc> findByTravelItineraryId(Long travelItineraryId);
}
