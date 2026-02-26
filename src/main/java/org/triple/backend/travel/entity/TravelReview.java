package org.triple.backend.travel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.user.entity.User;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
public class TravelReview extends BaseEntity {

    @Id
    @Column(name = "travel_review_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_itinerary_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TravelItinerary travelItinerary;

    @OneToMany(mappedBy = "travelReview", fetch = FetchType.LAZY)
    private List<TravelReviewImage> images = new ArrayList<>();

    private String content;

    private boolean isDeleted;

    private int view;
}
