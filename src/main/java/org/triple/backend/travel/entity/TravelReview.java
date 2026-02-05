package org.triple.backend.travel.entity;

import jakarta.persistence.*;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.user.entity.User;

@Entity
public class TravelReview extends BaseEntity {

    @Id
    @Column(name = "travel_review_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    private String content;

    private boolean isDeleted;
}
