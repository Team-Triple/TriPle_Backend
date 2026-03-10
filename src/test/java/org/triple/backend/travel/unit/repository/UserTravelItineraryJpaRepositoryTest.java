package org.triple.backend.travel.unit.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.triple.backend.common.annotation.RepositoryTest;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.travel.dto.request.TravelItinerarySaveRequestDto;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class UserTravelItineraryJpaRepositoryTest {

    @Autowired
    private UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private TravelItineraryJpaRepository travelItineraryJpaRepository;

    @Test
    @DisplayName("유저와 여행 일정 매핑을 저장한다.")
    void 유저와_여행_일정_매핑을_저장한다() {
        // given
        User user = userJpaRepository.save(User.builder().build());
        Group group = groupJpaRepository.save(createGroup());
        TravelItinerarySaveRequestDto request = new TravelItinerarySaveRequestDto(
                "제목",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group.getId(),
                "설명",
                "test-url"
        );
        TravelItinerary travel = travelItineraryJpaRepository.save(TravelItinerary.of(request, group));

        // when
        UserTravelItinerary saved = userTravelItineraryJpaRepository.save(
                UserTravelItinerary.of(user, travel, UserRole.LEADER)
        );
        UserTravelItinerary found = userTravelItineraryJpaRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found)
                .extracting("user", "travelItinerary", "userRole")
                .containsExactly(user, travel, UserRole.LEADER);
    }

    private Group createGroup() {
        return Group.create(GroupKind.PUBLIC, "모임", "설명", "http://thumb", 10);
    }
}
