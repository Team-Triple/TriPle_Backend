package org.triple.backend.group.unit.sevice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.request.GroupUpdateRequestDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.dto.response.GroupDetailResponseDto;
import org.triple.backend.group.dto.response.GroupUpdateResponseDto;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.JoinApplyJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.group.service.GroupService;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Autowired
    private JoinApplyJpaRepository joinApplyJpaRepository;

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

    @Test
    @DisplayName("내 그룹 첫 페이지 조회 시 JOINED 상태의 그룹만 size만큼 반환하고 hasNext와 nextCursor가 설정된다")
    void 내_그룹_첫_페이지_조회_시_JOINED_상태의_그룹만_size만큼_반환하고_hasNext와_nextCursor가_설정된다() {
        // given
        User me = userJpaRepository.save(User.builder()
                .providerId("kakao-my-groups-owner")
                .nickname("상윤")
                .email("my-groups-owner@test.com")
                .profileUrl("http://img")
                .build());

        for (int i = 1; i <= 12; i++) {
            Group group = groupJpaRepository.save(publicGroup("my-group-" + i));
            userGroupJpaRepository.save(UserGroup.create(me, group, Role.MEMBER));
        }

        // when
        GroupCursorResponseDto response = groupService.myGroups(null, 10, me.getId());

        // then
        assertThat(response.items()).hasSize(10);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isNotNull();
        assertThat(response.items())
                .allSatisfy(item -> assertThat(item.name()).startsWith("my-group-"));

        List<Long> ids = response.items().stream().map(GroupCursorResponseDto.GroupSummaryDto::groupId).toList();
        assertThat(ids).isSortedAccordingTo((a, b) -> Long.compare(b, a));
        assertThat(response.nextCursor()).isEqualTo(ids.get(ids.size() - 1));
    }

    @Test
    @DisplayName("내 그룹 다음 페이지 조회 시 cursor 기준으로 중복 없이 이어진다")
    void 내_그룹_다음_페이지_조회_시_cursor_기준으로_중복_없이_이어진다() {
        // given
        User me = userJpaRepository.save(User.builder()
                .providerId("kakao-my-groups-next")
                .nickname("상윤")
                .email("my-groups-next@test.com")
                .profileUrl("http://img")
                .build());

        for (int i = 1; i <= 12; i++) {
            Group group = groupJpaRepository.save(publicGroup("my-group-next-" + i));
            userGroupJpaRepository.save(UserGroup.create(me, group, Role.MEMBER));
        }

        // when
        GroupCursorResponseDto first = groupService.myGroups(null, 5, me.getId());
        GroupCursorResponseDto second = groupService.myGroups(first.nextCursor(), 5, me.getId());

        // then
        assertThat(first.items()).hasSize(5);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isNotNull();

        assertThat(second.items()).hasSize(5);
        assertThat(second.hasNext()).isTrue();
        assertThat(second.nextCursor()).isNotNull();
        assertThat(second.items())
                .allSatisfy(item -> assertThat(item.groupId()).isLessThan(first.nextCursor()));

        List<Long> firstIds = first.items().stream().map(GroupCursorResponseDto.GroupSummaryDto::groupId).toList();
        List<Long> secondIds = second.items().stream().map(GroupCursorResponseDto.GroupSummaryDto::groupId).toList();
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
    }

    @Test
    @DisplayName("내 그룹 조회 시 존재하지 않는 사용자면 USER_NOT_FOUND 예외가 발생한다")
    void 내_그룹_조회_시_존재하지_않는_사용자면_USER_NOT_FOUND_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> groupService.myGroups(null, 10, 999999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("내 그룹 조회 시 size는 1 이상 10 이하로 보정된다")
    void 내_그룹_조회_시_size는_일_이상_십_이하로_보정된다() {
        // given
        User me = userJpaRepository.save(User.builder()
                .providerId("kakao-my-groups-size")
                .nickname("상윤")
                .email("my-groups-size@test.com")
                .profileUrl("http://img")
                .build());

        for (int i = 1; i <= 20; i++) {
            Group group = groupJpaRepository.save(publicGroup("my-group-size-" + i));
            userGroupJpaRepository.save(UserGroup.create(me, group, Role.MEMBER));
        }

        // when
        GroupCursorResponseDto minSize = groupService.myGroups(null, 0, me.getId());
        GroupCursorResponseDto maxSize = groupService.myGroups(null, 999, me.getId());

        // then
        assertThat(minSize.items()).hasSize(1);
        assertThat(maxSize.items()).hasSize(10);
    }

    @Test
    @DisplayName("내 그룹 조회 시 PUBLIC과 PRIVATE 그룹을 모두 조회한다")
    void 내_그룹_조회_시_PUBLIC과_PRIVATE_그룹을_모두_조회한다() {
        // given
        User me = userJpaRepository.save(User.builder()
                .providerId("kakao-my-groups-kind")
                .nickname("상윤")
                .email("my-groups-kind@test.com")
                .profileUrl("http://img")
                .build());

        Group publicGroup = groupJpaRepository.save(Group.create(
                GroupKind.PUBLIC, "public-my-group", "설명", "thumb", 10
        ));
        Group privateGroup = groupJpaRepository.save(Group.create(
                GroupKind.PRIVATE, "private-my-group", "설명", "thumb", 10
        ));
        userGroupJpaRepository.save(UserGroup.create(me, publicGroup, Role.MEMBER));
        userGroupJpaRepository.save(UserGroup.create(me, privateGroup, Role.MEMBER));

        // when
        GroupCursorResponseDto response = groupService.myGroups(null, 10, me.getId());

        // then
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().stream().map(GroupCursorResponseDto.GroupSummaryDto::name).toList())
                .containsExactlyInAnyOrder("public-my-group", "private-my-group");

        List<Long> ids = response.items().stream().map(GroupCursorResponseDto.GroupSummaryDto::groupId).toList();
        List<Group> groups = groupJpaRepository.findAllById(ids);
        assertThat(groups).extracting(Group::getGroupKind)
                .containsExactlyInAnyOrder(GroupKind.PUBLIC, GroupKind.PRIVATE);
    }

    @Test
    @DisplayName("공개 그룹 상세 조회 시 상세 정보를 조회할 수 있다")
    void 공개_그룹_상세_조회_시_상세_정보를_조회할_수_있다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-detail")
                .nickname("상윤")
                .email("owner-detail@test.com")
                .profileUrl("http://img")
                .build());

        User viewer = userJpaRepository.save(User.builder()
                .providerId("kakao-viewer-detail")
                .nickname("조회자")
                .email("viewer-detail@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.save(group);

        // when
        GroupDetailResponseDto response = groupService.detail(savedGroup.getId(), viewer.getId());

        // then
        assertThat(response.name()).isEqualTo("여행모임");
        assertThat(response.description()).isEqualTo("설명");
        assertThat(response.groupKind()).isEqualTo(GroupKind.PUBLIC);
        assertThat(response.thumbNailUrl()).isEqualTo("thumb");
        assertThat(response.currentMemberCount()).isEqualTo(1);
        assertThat(response.memberLimit()).isEqualTo(10);
        assertThat(response.role()).isEqualTo(Role.GUEST);
        assertThat(response.users()).hasSize(1);
        assertThat(response.users().get(0).name()).isEqualTo("상윤");
        assertThat(response.users().get(0).isOwner()).isTrue();
        assertThat(response.recentPhotos()).isEmpty();
        assertThat(response.recentTravels()).isEmpty();
        assertThat(response.recentReviews()).isEmpty();
    }

    @Test
    @DisplayName("비공개 그룹 상세 조회 시 멤버가 아니면 NOT_GROUP_MEMBER 예외가 발생한다")
    void 비공개_그룹_상세_조회_시_멤버가_아니면_NOT_GROUP_MEMBER_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-private-detail")
                .nickname("상윤")
                .email("owner-private-detail@test.com")
                .profileUrl("http://img")
                .build());

        User outsider = userJpaRepository.save(User.builder()
                .providerId("kakao-outsider-private-detail")
                .nickname("민규")
                .email("outsider-private-detail@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PRIVATE, "비공개모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.save(group);

        // when & then
        assertThatThrownBy(() -> groupService.detail(savedGroup.getId(), outsider.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.NOT_GROUP_MEMBER);
                });
    }

    @Test
    @DisplayName("비공개 그룹 상세 조회 시 멤버는 상세 정보를 조회할 수 있다")
    void 비공개_그룹_상세_조회_시_멤버는_상세_정보를_조회할_수_있다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-private-member-detail")
                .nickname("상윤")
                .email("owner-private-member-detail@test.com")
                .profileUrl("http://img")
                .build());

        User member = userJpaRepository.save(User.builder()
                .providerId("kakao-member-private-member-detail")
                .nickname("민규")
                .email("member-private-member-detail@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PRIVATE, "비공개모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        Group savedGroup = groupJpaRepository.save(group);

        // when
        GroupDetailResponseDto response = groupService.detail(savedGroup.getId(), member.getId());

        // then
        assertThat(response.groupKind()).isEqualTo(GroupKind.PRIVATE);
        assertThat(response.users()).hasSize(2);
        assertThat(response.users().stream().map(GroupDetailResponseDto.UserDto::name).toList())
                .containsExactlyInAnyOrder("상윤", "민규");
        assertThat(response.users().stream().filter(GroupDetailResponseDto.UserDto::isOwner).count()).isEqualTo(1);
        assertThat(response.role()).isEqualTo(Role.MEMBER);
        assertThat(response.recentPhotos()).isEmpty();
        assertThat(response.recentTravels()).isEmpty();
        assertThat(response.recentReviews()).isEmpty();
    }

    @Test
    @DisplayName("그룹 삭제 시 Group이 삭제되고, 연관된 UserGroup과 JoinApply도 함께 삭제된다")
    void 그룹_삭제_시_Group이_삭제되고_연관된_UserGroup과_JoinApply도_함께_삭제된다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-1")
                .nickname("상윤")
                .email("test@test.com")
                .profileUrl("http://img")
                .build());

        User applicant = userJpaRepository.save(User.builder()
                .providerId("kakao-2")
                .nickname("민규")
                .email("mingyu@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);

        Group savedGroup = groupJpaRepository.save(group);

        JoinApply joinApply = JoinApply.builder()
                .group(savedGroup)
                .user(applicant)
                .build();

        joinApplyJpaRepository.save(joinApply);

        assertThat(groupJpaRepository.findById(savedGroup.getId())).isPresent();
        assertThat(userGroupJpaRepository.findAll()).hasSize(1);
        assertThat(joinApplyJpaRepository.findAll()).hasSize(1);

        // when
        groupService.delete(savedGroup.getId(), owner.getId());

        // then
        assertThat(groupJpaRepository.findById(savedGroup.getId())).isEmpty();
        assertThat(userGroupJpaRepository.findAll()).isEmpty();
        assertThat(joinApplyJpaRepository.findAll()).isEmpty();
    }


    @Test
    @DisplayName("존재하지 않는 그룹 ID로 삭제 요청 시 GROUP_NOT_FOUND 예외가 발생한다")
    void 존재하지_않는_그룹_ID로_삭제_요청_시_GROUP_NOT_FOUND_예외가_발생한다() {
        // given
        Long notExistGroupId = 999999L;
        Long userId = 1L;

        // when & then
        assertThatThrownBy(() -> groupService.delete(notExistGroupId, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_NOT_FOUND);
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동일한 그룹 삭제 요청이 동시에 들어오면 하나만 성공하고 나머지는 GROUP_NOT_FOUND가 발생한다")
    void 동일한_그룹_삭제_요청이_동시에_들어오면_하나만_성공하고_나머지는_GROUP_NOT_FOUND가_발생한다() throws InterruptedException {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner")
                .nickname("상윤")
                .email("owner@test.com")
                .profileUrl("http://img")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.save(group);

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable deleteTask = () -> {
            ready.countDown();
            try {
                start.await();
                groupService.delete(savedGroup.getId(), owner.getId());
                successCount.incrementAndGet();
            } catch (Throwable throwable) {
                failures.add(throwable);
            } finally {
                done.countDown();
            }
        };

        try {
            executorService.submit(deleteTask);
            executorService.submit(deleteTask);

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

            long groupNotFoundCount = failures.stream()
                    .filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .filter(e -> e.getErrorCode() == GroupErrorCode.GROUP_NOT_FOUND)
                    .count();

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failures).hasSize(1);
            assertThat(groupNotFoundCount).isEqualTo(1);
            assertThat(groupJpaRepository.findById(savedGroup.getId())).isEmpty();
        } finally {
            executorService.shutdownNow();
            joinApplyJpaRepository.deleteAllInBatch();
            userGroupJpaRepository.deleteAllInBatch();
            groupJpaRepository.deleteAllInBatch();
            userJpaRepository.deleteAllInBatch();
        }
    }

    @Test
    @DisplayName("그룹 수정 시 그룹 정보가 변경되고 수정 응답이 반환된다")
    void 그룹_수정_시_그룹_정보가_변경되고_수정_응답이_반환된다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner")
                .nickname("상윤")
                .email("owner@test.com")
                .profileUrl("http://img")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "기존모임", "기존설명", "https://example.com/old.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.save(group);

        GroupUpdateRequestDto request = new GroupUpdateRequestDto(
                GroupKind.PRIVATE,
                "수정된모임",
                "수정된설명",
                "https://example.com/new.png",
                20
        );

        // when
        GroupUpdateResponseDto response = groupService.update(request, savedGroup.getId(), owner.getId());

        // then
        Group updated = groupJpaRepository.findById(savedGroup.getId()).orElseThrow();
        assertThat(updated.getGroupKind()).isEqualTo(GroupKind.PRIVATE);
        assertThat(updated.getName()).isEqualTo("수정된모임");
        assertThat(updated.getDescription()).isEqualTo("수정된설명");
        assertThat(updated.getThumbNailUrl()).isEqualTo("https://example.com/new.png");
        assertThat(updated.getMemberLimit()).isEqualTo(20);

        assertThat(response.groupId()).isEqualTo(savedGroup.getId());
        assertThat(response.groupKind()).isEqualTo(GroupKind.PRIVATE);
        assertThat(response.name()).isEqualTo("수정된모임");
        assertThat(response.description()).isEqualTo("수정된설명");
        assertThat(response.thumbNailUrl()).isEqualTo("https://example.com/new.png");
        assertThat(response.memberLimit()).isEqualTo(20);
        assertThat(response.currentMemberCount()).isEqualTo(updated.getCurrentMemberCount());
    }

    @Test
    @DisplayName("존재하지 않는 그룹 수정 시 GROUP_NOT_FOUND 예외가 발생한다")
    void 존재하지_않는_그룹_수정_시_GROUP_NOT_FOUND_예외가_발생한다() {
        // given
        GroupUpdateRequestDto request = new GroupUpdateRequestDto(
                GroupKind.PRIVATE,
                "수정된모임",
                "수정된설명",
                "https://example.com/new.png",
                20
        );

        // when & then
        assertThatThrownBy(() -> groupService.update(request, 999999L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("그룹 소유자가 아니면 그룹 수정 시 NOT_GROUP_OWNER 예외가 발생한다")
    void 그룹_소유자가_아니면_그룹_수정_시_NOT_GROUP_OWNER_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner")
                .nickname("상윤")
                .email("owner@test.com")
                .profileUrl("http://img")
                .build());

        User notOwner = userJpaRepository.save(User.builder()
                .providerId("kakao-member")
                .nickname("민규")
                .email("member@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "기존모임", "기존설명", "https://example.com/old.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.save(group);

        GroupUpdateRequestDto request = new GroupUpdateRequestDto(
                GroupKind.PRIVATE,
                "수정된모임",
                "수정된설명",
                "https://example.com/new.png",
                20
        );

        // when & then
        assertThatThrownBy(() -> groupService.update(request, savedGroup.getId(), notOwner.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.NOT_GROUP_OWNER);
                });
    }

    @Test
    @DisplayName("그룹 소유자가 JOINED 멤버를 추방하면 대상은 LEFTED로 변경되고 그룹 인원이 감소한다")
    void 그룹_소유자가_JOINED_멤버를_추방하면_대상은_LEFTED로_변경되고_그룹_인원이_감소한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-kick")
                .nickname("상윤")
                .email("owner-kick@test.com")
                .profileUrl("http://img")
                .build());

        User member = userJpaRepository.save(User.builder()
                .providerId("kakao-member-kick")
                .nickname("민규")
                .email("member-kick@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addCurrentMemberCount();
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        JoinApply joinApply = JoinApply.create(member, savedGroup);
        joinApplyJpaRepository.saveAndFlush(joinApply);

        // when
        groupService.kick(savedGroup.getId(), owner.getId(), member.getId());

        // then
        UserGroup ownerUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), owner.getId()).orElseThrow();
        UserGroup targetUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), member.getId()).orElseThrow();
        Group updatedGroup = groupJpaRepository.findById(savedGroup.getId()).orElseThrow();

        assertThat(ownerUserGroup.getJoinStatus()).isEqualTo(JoinStatus.JOINED);
        assertThat(targetUserGroup.getJoinStatus()).isEqualTo(JoinStatus.LEFTED);
        assertThat(targetUserGroup.getLeftAt()).isNotNull();
        assertThat(updatedGroup.getCurrentMemberCount()).isEqualTo(1);
        assertThat(joinApplyJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), member.getId())).isEmpty();
    }

    @Test
    @DisplayName("그룹 소유자가 아니면 멤버 추방 시 NOT_GROUP_OWNER 예외가 발생한다")
    void 그룹_소유자가_아니면_멤버_추방_시_NOT_GROUP_OWNER_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-kick-auth")
                .nickname("상윤")
                .email("owner-kick-auth@test.com")
                .profileUrl("http://img")
                .build());

        User member = userJpaRepository.save(User.builder()
                .providerId("kakao-member-kick-auth")
                .nickname("민규")
                .email("member-kick-auth@test.com")
                .profileUrl("http://img2")
                .build());

        User other = userJpaRepository.save(User.builder()
                .providerId("kakao-other-kick-auth")
                .nickname("지원")
                .email("other-kick-auth@test.com")
                .profileUrl("http://img3")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addMember(other, Role.MEMBER);
        group.addCurrentMemberCount();
        group.addCurrentMemberCount();
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        assertThatThrownBy(() -> groupService.kick(savedGroup.getId(), member.getId(), other.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.NOT_GROUP_OWNER);
                });
    }

    @Test
    @DisplayName("그룹 소유자가 자기 자신을 추방하려고 하면 CANNOT_KICK_SELF 예외가 발생한다")
    void 그룹_소유자가_자기_자신을_추방하려고_하면_CANNOT_KICK_SELF_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-kick-self")
                .nickname("상윤")
                .email("owner-kick-self@test.com")
                .profileUrl("http://img")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        assertThatThrownBy(() -> groupService.kick(savedGroup.getId(), owner.getId(), owner.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.CANNOT_KICK_SELF);
                });
    }

    @Test
    @DisplayName("추방 대상이 OWNER이면 CANNOT_KICK_OWNER 예외가 발생한다")
    void 추방_대상이_OWNER이면_CANNOT_KICK_OWNER_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-kick-owner")
                .nickname("상윤")
                .email("owner-kick-owner@test.com")
                .profileUrl("http://img")
                .build());

        User otherOwner = userJpaRepository.save(User.builder()
                .providerId("kakao-other-owner-kick-owner")
                .nickname("민규")
                .email("other-owner-kick-owner@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        UserGroup secondOwner = UserGroup.create(otherOwner, savedGroup, Role.OWNER);
        userGroupJpaRepository.saveAndFlush(secondOwner);
        savedGroup.addCurrentMemberCount();
        groupJpaRepository.flush();

        // when & then
        assertThatThrownBy(() -> groupService.kick(savedGroup.getId(), owner.getId(), otherOwner.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.CANNOT_KICK_OWNER);
                });
    }

    @Test
    @DisplayName("그룹 멤버 탈퇴 시 LEFTED로 변경되고 그룹 인원이 감소하며 JoinApply가 삭제된다")
    void 그룹_멤버_탈퇴_시_LEFTED로_변경되고_그룹_인원이_감소하며_JoinApply가_삭제된다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-leave")
                .nickname("상윤")
                .email("owner-leave@test.com")
                .profileUrl("http://img")
                .build());

        User member = userJpaRepository.save(User.builder()
                .providerId("kakao-member-leave")
                .nickname("민규")
                .email("member-leave@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addCurrentMemberCount();
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        JoinApply approvedApply = JoinApply.create(member, savedGroup);
        approvedApply.approve();
        joinApplyJpaRepository.saveAndFlush(approvedApply);

        // when
        groupService.leave(savedGroup.getId(), member.getId());

        // then
        UserGroup leftUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), member.getId()).orElseThrow();
        Group updatedGroup = groupJpaRepository.findById(savedGroup.getId()).orElseThrow();

        assertThat(leftUserGroup.getJoinStatus()).isEqualTo(JoinStatus.LEFTED);
        assertThat(leftUserGroup.getLeftAt()).isNotNull();
        assertThat(updatedGroup.getCurrentMemberCount()).isEqualTo(1);
        assertThat(joinApplyJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), member.getId())).isEmpty();
    }

    @Test
    @DisplayName("그룹 소유자는 탈퇴할 수 없어 GROUP_OWNER_NOT_LEAVE 예외가 발생한다")
    void 그룹_소유자는_탈퇴할_수_없어_GROUP_OWNER_NOT_LEAVE_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-cannot-leave")
                .nickname("상윤")
                .email("owner-cannot-leave@test.com")
                .profileUrl("http://img")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        assertThatThrownBy(() -> groupService.leave(savedGroup.getId(), owner.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.GROUP_OWNER_NOT_LEAVE);
                });
    }

    @Test
    @DisplayName("이미 탈퇴한 멤버가 다시 탈퇴 요청하면 ALREADY_LEAVE_GROUP 예외가 발생한다")
    void 이미_탈퇴한_멤버가_다시_탈퇴_요청하면_ALREADY_LEAVE_GROUP_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-already-left")
                .nickname("상윤")
                .email("owner-already-left@test.com")
                .profileUrl("http://img")
                .build());

        User member = userJpaRepository.save(User.builder()
                .providerId("kakao-member-already-left")
                .nickname("민규")
                .email("member-already-left@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addCurrentMemberCount();
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        groupService.leave(savedGroup.getId(), member.getId());

        // when & then
        assertThatThrownBy(() -> groupService.leave(savedGroup.getId(), member.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.ALREADY_LEAVE_GROUP);
                });
    }

    @Test
    @DisplayName("그룹 멤버가 아닌 사용자가 탈퇴 요청하면 NOT_GROUP_MEMBER 예외가 발생한다")
    void 그룹_멤버가_아닌_사용자가_탈퇴_요청하면_NOT_GROUP_MEMBER_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-not-member-leave")
                .nickname("상윤")
                .email("owner-not-member-leave@test.com")
                .profileUrl("http://img")
                .build());

        User outsider = userJpaRepository.save(User.builder()
                .providerId("kakao-outsider-not-member-leave")
                .nickname("민규")
                .email("outsider-not-member-leave@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        assertThatThrownBy(() -> groupService.leave(savedGroup.getId(), outsider.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.NOT_GROUP_MEMBER);
                });
    }

    @Test
    @DisplayName("존재하지 않는 사용자가 탈퇴 요청하면 USER_NOT_FOUND 예외가 발생한다")
    void 존재하지_않는_사용자가_탈퇴_요청하면_USER_NOT_FOUND_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> groupService.leave(1L, 999999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("추방 대상이 JOINED가 아니면 NOT_GROUP_MEMBER 예외가 발생한다")
    void 추방_대상이_JOINED가_아니면_NOT_GROUP_MEMBER_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-kick-left-target")
                .nickname("상윤")
                .email("owner-kick-left-target@test.com")
                .profileUrl("http://img")
                .build());

        User member = userJpaRepository.save(User.builder()
                .providerId("kakao-member-kick-left-target")
                .nickname("민규")
                .email("member-kick-left-target@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addCurrentMemberCount();
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        UserGroup targetUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), member.getId()).orElseThrow();
        targetUserGroup.leave();
        groupJpaRepository.flush();

        // when & then
        assertThatThrownBy(() -> groupService.kick(savedGroup.getId(), owner.getId(), member.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.NOT_GROUP_MEMBER);
                });
    }

    @Test
    @DisplayName("그룹 소유권 이전 시 대상은 OWNER가 되고 기존 소유자는 MEMBER가 된다")
    void 그룹_소유권_이전_시_대상은_OWNER가_되고_기존_소유자는_MEMBER가_된다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-transfer")
                .nickname("상윤")
                .email("owner-transfer@test.com")
                .profileUrl("http://img")
                .build());
        User target = userJpaRepository.save(User.builder()
                .providerId("kakao-target-transfer")
                .nickname("민규")
                .email("target-transfer@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(target, Role.MEMBER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when
        groupService.ownerTransfer(savedGroup.getId(), target.getId(), owner.getId());

        // then
        UserGroup ownerUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), owner.getId()).orElseThrow();
        UserGroup targetUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), target.getId()).orElseThrow();

        assertThat(ownerUserGroup.getRole()).isEqualTo(Role.MEMBER);
        assertThat(targetUserGroup.getRole()).isEqualTo(Role.OWNER);
        assertThat(ownerUserGroup.getJoinStatus()).isEqualTo(JoinStatus.JOINED);
        assertThat(targetUserGroup.getJoinStatus()).isEqualTo(JoinStatus.JOINED);
    }

    @Test
    @DisplayName("그룹 소유자가 자기 자신에게 소유권을 이전하면 CANNOT_OWNER_DEMOTE_SELF 예외가 발생한다")
    void 그룹_소유자가_자기_자신에게_소유권을_이전하면_CANNOT_OWNER_DEMOTE_SELF_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-self-transfer")
                .nickname("상윤")
                .email("owner-self-transfer@test.com")
                .profileUrl("http://img")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        assertThatThrownBy(() -> groupService.ownerTransfer(savedGroup.getId(), owner.getId(), owner.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.CANNOT_OWNER_DEMOTE_SELF);
                });
    }

    @Test
    @DisplayName("그룹 소유자가 아닌 사용자가 소유권 이전을 요청하면 NOT_GROUP_OWNER 예외가 발생한다")
    void 그룹_소유자가_아닌_사용자가_소유권_이전을_요청하면_NOT_GROUP_OWNER_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-transfer-auth")
                .nickname("상윤")
                .email("owner-transfer-auth@test.com")
                .profileUrl("http://img")
                .build());
        User member = userJpaRepository.save(User.builder()
                .providerId("kakao-member-transfer-auth")
                .nickname("민규")
                .email("member-transfer-auth@test.com")
                .profileUrl("http://img2")
                .build());
        User target = userJpaRepository.save(User.builder()
                .providerId("kakao-target-transfer-auth")
                .nickname("지원")
                .email("target-transfer-auth@test.com")
                .profileUrl("http://img3")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addMember(target, Role.MEMBER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        assertThatThrownBy(() -> groupService.ownerTransfer(savedGroup.getId(), target.getId(), member.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.NOT_GROUP_OWNER);
                });
    }

    @Test
    @DisplayName("소유권 이전 대상이 그룹 멤버가 아니면 NOT_GROUP_MEMBER 예외가 발생한다")
    void 소유권_이전_대상이_그룹_멤버가_아니면_NOT_GROUP_MEMBER_예외가_발생한다() {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-transfer-target")
                .nickname("상윤")
                .email("owner-transfer-target@test.com")
                .profileUrl("http://img")
                .build());
        User outsider = userJpaRepository.save(User.builder()
                .providerId("kakao-outsider-transfer-target")
                .nickname("민규")
                .email("outsider-transfer-target@test.com")
                .profileUrl("http://img2")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        assertThatThrownBy(() -> groupService.ownerTransfer(savedGroup.getId(), outsider.getId(), owner.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.NOT_GROUP_MEMBER);
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동일한 그룹 소유권 이전 요청이 동시에 들어오면 하나만 성공하고 나머지는 NOT_GROUP_OWNER가 발생한다")
    void 동일한_그룹_소유권_이전_요청이_동시에_들어오면_하나만_성공하고_나머지는_NOT_GROUP_OWNER가_발생한다() throws InterruptedException {
        // given
        User owner = userJpaRepository.save(User.builder()
                .providerId("kakao-owner-transfer-concurrency")
                .nickname("상윤")
                .email("owner-transfer-concurrency@test.com")
                .profileUrl("http://img")
                .build());
        User target1 = userJpaRepository.save(User.builder()
                .providerId("kakao-target1-transfer-concurrency")
                .nickname("민규")
                .email("target1-transfer-concurrency@test.com")
                .profileUrl("http://img2")
                .build());
        User target2 = userJpaRepository.save(User.builder()
                .providerId("kakao-target2-transfer-concurrency")
                .nickname("지원")
                .email("target2-transfer-concurrency@test.com")
                .profileUrl("http://img3")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(target1, Role.MEMBER);
        group.addMember(target2, Role.MEMBER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable transferToTarget1 = () -> {
            ready.countDown();
            try {
                start.await();
                groupService.ownerTransfer(savedGroup.getId(), target1.getId(), owner.getId());
                successCount.incrementAndGet();
            } catch (Throwable throwable) {
                failures.add(throwable);
            } finally {
                done.countDown();
            }
        };

        Runnable transferToTarget2 = () -> {
            ready.countDown();
            try {
                start.await();
                groupService.ownerTransfer(savedGroup.getId(), target2.getId(), owner.getId());
                successCount.incrementAndGet();
            } catch (Throwable throwable) {
                failures.add(throwable);
            } finally {
                done.countDown();
            }
        };

        try {
            executorService.submit(transferToTarget1);
            executorService.submit(transferToTarget2);

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

            long notGroupOwnerCount = failures.stream()
                    .filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .filter(e -> e.getErrorCode() == GroupErrorCode.NOT_GROUP_OWNER)
                    .count();

            UserGroup ownerUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), owner.getId()).orElseThrow();
            UserGroup target1UserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), target1.getId()).orElseThrow();
            UserGroup target2UserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), target2.getId()).orElseThrow();

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failures).hasSize(1);
            assertThat(notGroupOwnerCount).isEqualTo(1);
            assertThat(ownerUserGroup.getRole()).isEqualTo(Role.MEMBER);
            assertThat(List.of(target1UserGroup.getRole(), target2UserGroup.getRole()))
                    .containsExactlyInAnyOrder(Role.OWNER, Role.MEMBER);
        } finally {
            executorService.shutdownNow();
            joinApplyJpaRepository.deleteAllInBatch();
            userGroupJpaRepository.deleteAllInBatch();
            groupJpaRepository.deleteAllInBatch();
            userJpaRepository.deleteAllInBatch();
        }
    }

}
