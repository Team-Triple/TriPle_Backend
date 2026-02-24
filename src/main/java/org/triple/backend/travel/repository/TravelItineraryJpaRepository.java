package org.triple.backend.travel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.triple.backend.travel.entity.TravelItinerary;

import java.util.Optional;

public interface TravelItineraryJpaRepository extends JpaRepository<TravelItinerary, Long> {
    Optional<TravelItinerary> findById(Long travelId);

    @Query("select t from TravelItinerary t where t.id = :travelId and t.isDeleted = false")
    Optional<TravelItinerary> findByIdAndIsDeletedFalse(@Param("travelId") Long travelId);
}
