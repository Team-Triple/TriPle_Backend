package org.triple.backend.travel.entity;

import jakarta.persistence.*;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.group.entity.group.Group;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class TravelItinerary extends BaseEntity {

    @Id
    @Column(name = "travel_itinerary_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Group group;

    private String description;

    private String thumbnailUrl;

    private int memberLimit;

    private int memberCount = 1;

    private boolean isDeleted;

    @OneToMany(mappedBy = "travelItinerary")
    private List<UserTravelItinerary> userTravelItineraries = new ArrayList<>();
}
