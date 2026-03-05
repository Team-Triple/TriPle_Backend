package org.triple.backend.group.unit.sevice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.JoinApplyJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.group.service.GroupService;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.TravelReviewImageJpaRepository;
import org.triple.backend.travel.repository.TravelReviewJpaRepository;
import org.triple.backend.user.repository.UserJpaRepository;
import org.triple.backend.user.service.UserFinder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceSearchFullTextTest {

    @Mock
    private GroupJpaRepository groupJpaRepository;

    @Mock
    private UserGroupJpaRepository userGroupJpaRepository;

    @Mock
    private JoinApplyJpaRepository joinApplyJpaRepository;

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private TravelItineraryJpaRepository travelItineraryJpaRepository;

    @Mock
    private TravelReviewJpaRepository travelReviewJpaRepository;

    @Mock
    private TravelReviewImageJpaRepository travelReviewImageJpaRepository;

    @Mock
    private UserFinder userFinder;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(
                groupJpaRepository,
                userGroupJpaRepository,
                joinApplyJpaRepository,
                travelItineraryJpaRepository,
                travelReviewJpaRepository,
                travelReviewImageJpaRepository,
                userJpaRepository,
                userFinder
        );
    }

    @Test
    @DisplayName("FULLTEXT 검색은 키워드를 boolean mode 쿼리로 변환해 첫 페이지를 조회한다")
    void FULLTEXT_검색은_키워드를_boolean_mode_쿼리로_변환해_첫_페이지를_조회한다() {
        // given
        Group g1 = newGroup(30L, "제주여행");
        Group g2 = newGroup(29L, "제주모임");

        when(groupJpaRepository.findFirstPageByKeywordFullText(eq("+제주* +여행*"), eq("PUBLIC"), any(Pageable.class)))
                .thenReturn(List.of(g1, g2));

        // when
        GroupCursorResponseDto response = groupService.search(" 제주!!   여행? ", null, 10);

        // then
        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.items())
                .extracting(GroupCursorResponseDto.GroupSummaryDto::name)
                .containsExactly("제주여행", "제주모임");
    }

    @Test
    @DisplayName("FULLTEXT 검색은 구두점을 단어 경계로 처리해 boolean mode 쿼리로 변환한다")
    void FULLTEXT_검색은_구두점을_단어_경계로_처리해_boolean_mode_쿼리로_변환한다() {
        // given
        Group g1 = newGroup(31L, "jeju-travelers");

        when(groupJpaRepository.findFirstPageByKeywordFullText(eq("+jeju* +travel* +plan*"), eq("PUBLIC"), any(Pageable.class)))
                .thenReturn(List.of(g1));

        // when
        GroupCursorResponseDto response = groupService.search("jeju-travel, plan", null, 10);

        // then
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).name()).isEqualTo("jeju-travelers");
    }

    @Test
    @DisplayName("FULLTEXT 검색 다음 페이지는 커서 조건과 pageSize+1로 조회하고 hasNext를 계산한다")
    void FULLTEXT_검색_다음_페이지는_커서_조건과_pageSize_플러스_일로_조회하고_hasNext를_계산한다() {
        // given
        Group g1 = newGroup(49L, "제주49");
        Group g2 = newGroup(48L, "제주48");
        Group g3 = newGroup(47L, "제주47");

        when(groupJpaRepository.findNextPageByKeywordFullText(eq("+제주* +여행*"), eq(50L), eq("PUBLIC"), any(Pageable.class)))
                .thenReturn(List.of(g1, g2, g3));

        // when
        GroupCursorResponseDto response = groupService.search("제주 여행", 50L, 2);

        // then
        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(48L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(groupJpaRepository).findNextPageByKeywordFullText(eq("+제주* +여행*"), eq(50L), eq("PUBLIC"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    @DisplayName("FULLTEXT 검색어가 특수문자만 있으면 빈 결과를 반환한다")
    void FULLTEXT_검색어가_특수문자만_있으면_빈_결과를_반환한다() {
        // when
        GroupCursorResponseDto response = groupService.search(" !!! ??? ", null, 10);

        // then
        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();

        verify(groupJpaRepository, never()).findFirstPageByKeywordFullText(any(), any(), any());
        verify(groupJpaRepository, never()).findNextPageByKeywordFullText(any(), any(), any(), any());
    }

    @Test
    @DisplayName("검색어 길이가 20자를 초과하면 INVALID_SEARCH_KEYWORD_LENGTH 예외가 발생한다")
    void 검색어_길이가_20자를_초과하면_INVALID_SEARCH_KEYWORD_LENGTH_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> groupService.search("aaaaaaaaaaaaaaaaaaaaa", null, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.INVALID_SEARCH_KEYWORD_LENGTH);
                });

        verify(groupJpaRepository, never()).findFirstPageByKeywordFullText(any(), any(), any());
    }

    private Group newGroup(Long id, String name) {
        Group group = Group.create(GroupKind.PUBLIC, name, "desc", "thumb", 10);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }
}
