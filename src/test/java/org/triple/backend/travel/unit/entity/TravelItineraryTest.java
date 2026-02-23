package org.triple.backend.travel.unit.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.travel.dto.request.TravelSaveRequestDto;
import org.triple.backend.travel.entity.TravelItinerary;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TravelItineraryTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 2, 14, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 2, 16, 0, 0);

    @Test
    @DisplayName("여행 일정 생성 시 memberCount는 1이다.")
    void 멤버카운트_초기값_1() {
        TravelSaveRequestDto req = new TravelSaveRequestDto(
                "title",
                START,
                END,
                1L,
                "desc",
                "test-url",
                5
        );

        TravelItinerary itinerary = TravelItinerary.of(req, createGroup());

        assertThat(itinerary.getMemberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("memberLimit이 1이면 예외가 발생하지 않는다.")
    void 멤버제한_1_유효() {
        TravelItinerary itinerary = new TravelItinerary(
                "title",
                START,
                END,
                createGroup(),
                "desc",
                "test-url",
                1,
                1,
                false
        );

        assertThat(itinerary.getMemberLimit()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    @DisplayName("불변식 위반 시 예외를 던진다.")
    void 불변식_위반_예외(
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Group group,
            int memberLimit
    ) {
        assertThatThrownBy(() -> new TravelItinerary(
                title,
                startAt,
                endAt,
                group,
                "desc",
                "test-url",
                memberLimit,
                1,
                false
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> invalidArguments() {
        return Stream.of(
                Arguments.of(null, START, END, createGroup(), 5),
                Arguments.of("   ", START, END, createGroup(), 5),
                Arguments.of("title", null, END, createGroup(), 5),
                Arguments.of("title", START, null, createGroup(), 5),
                Arguments.of("title", START, END, null, 5),
                Arguments.of("title", START, END, createGroup(), 0),
                Arguments.of("title", START, END, createGroup(), 21),
                Arguments.of("title", END, START, createGroup(), 5),
                Arguments.of("title", START, START, createGroup(), 5)
        );
    }

    private static Group createGroup() {
        return Group.create(GroupKind.PUBLIC, "group", "desc", "thumb", 10);
    }
}
