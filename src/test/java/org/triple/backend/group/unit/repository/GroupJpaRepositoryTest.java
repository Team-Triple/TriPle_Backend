package org.triple.backend.group.unit.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.triple.backend.common.annotation.RepositoryTest;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.repository.GroupJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class GroupJpaRepositoryTest {

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Test
    @DisplayName("저장한 그룹을 ID로 조회시 Group 엔티티를 반환한다.")
    void 저장한_그룹을_ID로_조회시_Group_엔티티를_반환한다() {
        // given
        Group group = Group.builder()
                .name("여행모임")
                .description("3월 일본 여행")
                .memberLimit(10)
                .groupKind(GroupKind.PUBLIC)
                .thumbNailUrl("https://example.com/thumb.png")
                .build();

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
}
