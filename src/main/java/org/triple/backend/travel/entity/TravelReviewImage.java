package org.triple.backend.travel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.triple.backend.user.entity.User;

@Entity
@Getter
public class TravelReviewImage {

    @Id
    @Column(name = "travel_review_image_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_review_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TravelReview travelReview;

    private String reviewImageUrl;
}
