package org.triple.backend.group.unit.sevice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.group.service.GroupService;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.triple.backend.group.fixture.GroupFixtures.privateGroup;
import static org.triple.backend.group.fixture.GroupFixtures.publicGroup;

@ServiceTest
@Import(GroupService.class)
public class GroupServiceTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private UserGroupJpaRepository userGroupJpaRepository;

    @Test
    @DisplayName("새로운 그룹 생성 시 그룹 정보가 올바르게 저장되고 생성자가 방장으로 등록된다")
    void 새로운_그룹_생성_시_그룹_정보가_올바르게_저장되고_생성자가_방장으로_등록된다() {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-1")
                        .nickname("상윤")
                        .email("test@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        CreateGroupRequestDto req = new CreateGroupRequestDto(
                "여행모임",
                "3월 일본 여행",
                10,
                GroupKind.PUBLIC,
                "https://example.com/thumb.png"
        );

        // when
        CreateGroupResponseDto res = groupService.create(req, owner.getId());

        // then
        Group savedGroup = groupJpaRepository.findById(res.groupId()).orElseThrow();
        assertThat(savedGroup.getName()).isEqualTo("여행모임");
        assertThat(savedGroup.getDescription()).isEqualTo("3월 일본 여행");
        assertThat(savedGroup.getMemberLimit()).isEqualTo(10);
        assertThat(savedGroup.getGroupKind()).isEqualTo(GroupKind.PUBLIC);
        assertThat(savedGroup.getThumbNailUrl()).isEqualTo("https://example.com/thumb.png");

        List<UserGroup> memberships = userGroupJpaRepository.findAll();
        assertThat(memberships).hasSize(1);

        UserGroup userGroup = memberships.get(0);
        assertThat(userGroup.getUser().getId()).isEqualTo(owner.getId());
        assertThat(userGroup.getGroup().getId()).isEqualTo(savedGroup.getId());
        assertThat(userGroup.getRole()).isEqualTo(Role.OWNER);
        assertThat(userGroup.getJoinStatus()).isEqualTo(JoinStatus.JOINED);
        assertThat(userGroup.getJoinedAt()).isNotNull();
    }

    @Test
    @DisplayName("공개 그룹 첫 페이지 조회 시 PUBLIC 그룹만 size만큼 반환하고 hasNext와 nextCursor가 올바르게 설정된다")
    void 공개_그룹_첫_페이지_조회_시_PUBLIC_그룹만_size만큼_반환하고_hasNext와_nextCursor가_올바르게_설정된다() {

        for (int i = 1; i <= 12; i++) {
            groupJpaRepository.save(publicGroup("public-" + i));
        }
        for (int i = 1; i <= 3; i++) {
            groupJpaRepository.save(privateGroup("private-" + i));
        }

        // when
        GroupCursorResponseDto res = groupService.browsePublicGroups(null, 10);

        // then
        assertThat(res.items()).hasSize(10);
        assertThat(res.hasNext()).isTrue();
        assertThat(res.nextCursor()).isNotNull();

        assertThat(res.items())
                .allSatisfy(item -> assertThat(item.name()).startsWith("public-"));

        List<Long> ids = res.items().stream().map(GroupCursorResponseDto.GroupSummaryDto::groupId).toList();
        assertThat(ids).isSortedAccordingTo((a, b) -> Long.compare(b, a));

        assertThat(res.nextCursor()).isEqualTo(ids.get(ids.size() - 1));
    }

    @Test
    @DisplayName("공개 그룹 다음 페이지 조회 시 cursor 기준으로 id가 작은 그룹들만 반환된다")
    void 공개_그룹_다음_페이지_조회_시_cursor_기준으로_id가_작은_그룹들만_반환된다() {

        for (int i = 1; i <= 15; i++) {
            groupJpaRepository.save(publicGroup("public-" + i));
        }

        GroupCursorResponseDto first = groupService.browsePublicGroups(null, 10);
        Long cursor = first.nextCursor();

        GroupCursorResponseDto second = groupService.browsePublicGroups(cursor, 10);

        assertThat(second.items()).hasSize(5);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
        assertThat(second.items())
                .allSatisfy(item -> assertThat(item.groupId()).isLessThan(cursor));
    }

    @Test
    @DisplayName("공개 그룹 조회 시 크기는 1 이상 10 이하로 보정된다")
    void 공개_그룹_조회_시_크기는_1이상_10이하로_보정된다() {

        for (int i = 1; i <= 20; i++) {
            groupJpaRepository.save(publicGroup("public-" + i));
        }

        GroupCursorResponseDto r1 = groupService.browsePublicGroups(null, 0);
        assertThat(r1.items()).hasSize(1);

        GroupCursorResponseDto r2 = groupService.browsePublicGroups(null, 999);
        assertThat(r2.items()).hasSize(10);
    }
}
