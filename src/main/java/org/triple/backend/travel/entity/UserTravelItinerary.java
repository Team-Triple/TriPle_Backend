package org.triple.backend.travel.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.triple.backend.user.entity.User;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTravelItinerary {

    @Id
    @Column(name = "user_travel_itinerary_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_itinerary_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TravelItinerary travelItinerary;

    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    @Builder(access = AccessLevel.PROTECTED)
    public UserTravelItinerary(User user, TravelItinerary travelItinerary, UserRole userRole) {
        this.user = validateUser(user);
        this.travelItinerary = validateTravelItinerary(travelItinerary);
        this.userRole = validateUserRole(userRole);
    }

    public static UserTravelItinerary of(User user, TravelItinerary savedTravelItinerary, UserRole userRole) {
        return UserTravelItinerary.builder()
                .user(user)
                .travelItinerary(savedTravelItinerary)
                .userRole(userRole)
                .build();
    }

    private static User validateUser(User user) {
        if (user == null) throw new IllegalArgumentException("user는 Null일 수 없습니다.");
        return user;
    }

    private static TravelItinerary validateTravelItinerary(TravelItinerary travelItinerary) {
        if (travelItinerary == null) throw new IllegalArgumentException("travelItinerary는 Null일 수 없습니다.");
        return travelItinerary;
    }

    private static UserRole validateUserRole(UserRole userRole) {
        if (userRole == null) throw new IllegalArgumentException("userRole은 Null일 수 없습니다.");
        return userRole;
    }
}
