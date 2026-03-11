package org.triple.backend.travel.unit.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.travel.dto.request.TravelItinerarySaveRequestDto;
import org.triple.backend.travel.dto.request.TravelItineraryUpdateRequestDto;
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
        TravelItinerarySaveRequestDto req = new TravelItinerarySaveRequestDto(
                "title",
                START,
                END,
                1L,
                "desc"
        );

        TravelItinerary itinerary = TravelItinerary.of(req, createGroup());

        assertThat(itinerary.getMemberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("멤버 수는 생성 시 1명으로 시작한다.")
    void 멤버수_초기값_검증() {
        TravelItinerary itinerary = new TravelItinerary(
                "title",
                START,
                END,
                createGroup(),
                "desc",
                1,
                false
        );

        assertThat(itinerary.getMemberCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    @DisplayName("불변식 위반 시 예외를 던진다.")
    void 불변식_위반_예외(
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Group group
    ) {
        assertThatThrownBy(() -> new TravelItinerary(
                title,
                startAt,
                endAt,
                group,
                "desc",
                1,
                false
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidTravelSaveRequestDto")
    @DisplayName("업데이트 시 불변식 위반 시 예외를 던진다.")
    void 업데이트_불변식_위반(
            TravelItineraryUpdateRequestDto travelItineraryUpdateRequestDto
    ) {
        TravelItinerary travelItinerary = createTravelItinerary();
        assertThatThrownBy(() -> travelItinerary.updateTravelItinerary(travelItineraryUpdateRequestDto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("삭제 시 여행 일정은 삭제 상태가 된다.")
    void 삭제_상태_변경() {
        TravelItinerary travelItinerary = createTravelItinerary();

        travelItinerary.deleteTravelItinerary();

        assertThat(travelItinerary.isDeleted()).isTrue();
    }

    private static Stream<Arguments> invalidArguments() {
        return Stream.of(
                Arguments.of(null, START, END, createGroup()),
                Arguments.of("   ", START, END, createGroup()),
                Arguments.of("title", null, END, createGroup()),
                Arguments.of("title", START, null, createGroup()),
                Arguments.of("title", START, END, null),
                Arguments.of("title", END, START, createGroup()),
                Arguments.of("title", START, START, createGroup())
        );
    }

    private static Stream<Arguments> invalidTravelSaveRequestDto() {
        return Stream.of(
                Arguments.of(new TravelItineraryUpdateRequestDto(null, END, START, null)),
                Arguments.of(new TravelItineraryUpdateRequestDto(null, END, null, null)),
                Arguments.of(new TravelItineraryUpdateRequestDto(null, null, START, null))
        );
    }

    private TravelItinerary createTravelItinerary() {
        return new TravelItinerary(
                "test", START, END, createGroup(), "test-description", 1, false);
    }

    private static Group createGroup() {
        return Group.create(GroupKind.PUBLIC, "group", "desc", "thumb", 10);
    }
}
