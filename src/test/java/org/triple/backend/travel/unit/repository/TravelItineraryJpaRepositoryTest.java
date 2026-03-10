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
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class TravelItineraryJpaRepositoryTest {

    @Autowired
    private TravelItineraryJpaRepository travelItineraryJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Test
    @DisplayName("여행 일정을 저장하고 조회한다.")
    void 여행_일정을_저장하고_조회한다() {
        // given
        Group group = groupJpaRepository.save(createGroup());
        TravelItinerarySaveRequestDto request = new TravelItinerarySaveRequestDto(
                "제목",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group.getId(),
                "설명",
                "test-url"
        );

        // when
        TravelItinerary saved = travelItineraryJpaRepository.save(TravelItinerary.of(request, group));
        TravelItinerary found = travelItineraryJpaRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found)
                .extracting("title", "description", "thumbnailUrl", "memberCount", "group")
                .containsExactly("제목", "설명", "test-url", 1, group);
    }

    @Test
    @DisplayName("삭제된 여행 일정은 활성 조회에서 제외된다.")
    void 삭제된_여행_일정_활성조회_제외() {
        Group group = groupJpaRepository.save(createGroup());
        TravelItinerarySaveRequestDto request = new TravelItinerarySaveRequestDto(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group.getId(),
                "desc",
                "test-url"
        );

        TravelItinerary saved = travelItineraryJpaRepository.save(TravelItinerary.of(request, group));
        saved.deleteTravelItinerary();
        travelItineraryJpaRepository.flush();

        assertThat(travelItineraryJpaRepository.findByIdAndIsDeletedFalse(saved.getId())).isEmpty();
    }

    private Group createGroup() {
        return Group.create(GroupKind.PUBLIC, "모임", "설명", "http://thumb", 10);
    }
}
