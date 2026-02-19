package org.triple.backend.group.unit.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.triple.backend.common.annotation.RepositoryTest;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.repository.GroupJpaRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.triple.backend.group.fixture.GroupFixtures.privateGroup;
import static org.triple.backend.group.fixture.GroupFixtures.publicGroup;

@RepositoryTest
public class GroupJpaRepositoryTest {

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Test
    @DisplayName("저장한 그룹을 ID로 조회시 Group 엔티티를 반환한다.")
    void 저장한_그룹을_ID로_조회시_Group_엔티티를_반환한다() {
        // given
        Group group = Group.create(
                GroupKind.PUBLIC,
                "여행모임",
                "3월 일본 여행",
                "https://example.com/thumb.png",
                10
        );

        // when
        Group saved = groupJpaRepository.save(group);

        // then
        assertThat(saved.getId()).isNotNull();

        Group found = groupJpaRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("여행모임");
        assertThat(found.getDescription()).isEqualTo("3월 일본 여행");
        assertThat(found.getMemberLimit()).isEqualTo(10);
        assertThat(found.getGroupKind()).isEqualTo(GroupKind.PUBLIC);
        assertThat(found.getThumbNailUrl()).isEqualTo("https://example.com/thumb.png");
        assertThat(found.getCurrentMemberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 그룹이면 empty를 반환한다")
    void 존재하지_않는_사용자면_empty를_반환한다() {
        // when
        var result = groupJpaRepository.findById(999999L);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("PUBLIC 첫 페이지 조회시 PUBLIC만 id 내림차순으로 size만큼 조회된다")
    void 첫_페이지_조회시_PUBLIC만_id_내림차순으로_size_만큼_조회한다() {
        // given
        for (int i = 1; i <= 12; i++) {
            groupJpaRepository.save(publicGroup("public-" + i));
        }
        for (int i = 1; i <= 3; i++) {
            groupJpaRepository.save(privateGroup("private-" + i));
        }

        Pageable pageable = PageRequest.of(0, 10);

        // when
        List<Group> result = groupJpaRepository.findPublicFirstPage(GroupKind.PUBLIC, pageable);

        // then
        assertThat(result).hasSize(10);
        assertThat(result).allSatisfy(g -> assertThat(g.getGroupKind()).isEqualTo(GroupKind.PUBLIC));

        List<Long> ids = result.stream().map(Group::getId).toList();
        assertThat(ids).isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }


    @Test
    @DisplayName("PUBLIC 다음 페이지 조회: cursor 보다 작은 id만 id 내림차순으로 조회된다")
    void PUBLIC_다음_페이지_조회_cursor보다_작은_id만_id_내림차순으로_조회된다() {
        // given
        for (int i = 1; i <= 15; i++) {
            groupJpaRepository.save(publicGroup("public-" + i));
        }

        Pageable firstPageable = PageRequest.of(0, 10);
        List<Group> first = groupJpaRepository.findPublicFirstPage(GroupKind.PUBLIC, firstPageable);

        Long cursor = first.get(first.size() - 1).getId();

        Pageable nextPageable = PageRequest.of(0, 10);

        // when
        List<Group> next = groupJpaRepository.findPublicNextPage(GroupKind.PUBLIC, cursor, nextPageable);

        // then
        assertThat(next).hasSize(5);
        assertThat(next).allSatisfy(g -> {
            assertThat(g.getGroupKind()).isEqualTo(GroupKind.PUBLIC);
            assertThat(g.getId()).isLessThan(cursor);
        });

        List<Long> ids = next.stream().map(Group::getId).toList();
        assertThat(ids).isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }
}
