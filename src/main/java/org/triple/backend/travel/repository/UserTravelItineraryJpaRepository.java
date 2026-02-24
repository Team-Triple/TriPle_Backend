package org.triple.backend.travel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.travel.entity.UserTravelItinerary;

public interface UserTravelItineraryJpaRepository extends JpaRepository<UserTravelItinerary, Long> {
}
