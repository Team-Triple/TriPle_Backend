package org.triple.backend.travel.unit.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.user.entity.User;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTravelItineraryTest {

    @Test
    @DisplayName("유저 여행 일정 엔티티는 유저, 여행 일정, 역할을 저장한다.")
    void 생성_성공() {
        UserTravelItinerary link = UserTravelItinerary.of(
                new User(),
                new TravelItinerary(
                        "title",
                        LocalDateTime.of(2026, 2, 14, 0, 0),
                        LocalDateTime.of(2026, 2, 16, 0, 0),
                        createGroup(),
                        "desc",
                        1,
                        false
                ),
                UserRole.LEADER
        );

        assertThat(link.getUser()).isNotNull();
        assertThat(link.getTravelItinerary()).isNotNull();
        assertThat(link.getUserRole()).isEqualTo(UserRole.LEADER);
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    @DisplayName("필수 값 누락 시 예외를 던진다.")
    void 필수값_누락_예외(
            User user,
            TravelItinerary travelItinerary,
            UserRole userRole
    ) {
        assertThatThrownBy(() -> UserTravelItinerary.of(user, travelItinerary, userRole))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> invalidArguments() {
        TravelItinerary itinerary = new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                createGroup(),
                "desc",
                1,
                false
        );
        User user = new User();

        return Stream.of(
                Arguments.of(null, itinerary, UserRole.MEMBER),
                Arguments.of(user, null, UserRole.MEMBER),
                Arguments.of(user, itinerary, null)
        );
    }

    private static Group createGroup() {
        return Group.create(GroupKind.PUBLIC, "group", "desc", "thumb", 10);
    }
}
