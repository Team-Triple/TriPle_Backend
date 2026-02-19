package org.triple.backend.group.unit.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GroupTest {
    @Test
    @DisplayName("유효한 값이면 Group이 생성되고 기본 currentMemberCount는 1이다")
    void 유효한_값이면_Group이_생성되고_기본_currentMemberCount는_1이다() {
        // given
        GroupKind kind = GroupKind.PUBLIC;

        // when
        Group group = Group.create(kind, "여행모임", "3월 일본 여행", "https://example.com/thumb.png", 10);

        // then
        assertThat(group.getGroupKind()).isEqualTo(GroupKind.PUBLIC);
        assertThat(group.getName()).isEqualTo("여행모임");
        assertThat(group.getDescription()).isEqualTo("3월 일본 여행");
        assertThat(group.getThumbNailUrl()).isEqualTo("https://example.com/thumb.png");
        assertThat(group.getMemberLimit()).isEqualTo(10);
        assertThat(group.getCurrentMemberCount()).isEqualTo(1);
        assertThat(group.getUserGroups()).isNotNull();
        assertThat(group.getUserGroups()).isEmpty();
    }

    @Test
    @DisplayName("create에서 kind가 null이면 IllegalArgumentException이 발생한다")
    void create에서_kind가_null이면_IllegalArgumentException이_발생한다() {
        assertThatThrownBy(() -> Group.create(null, "여행모임", "설명", "thumb", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("그룹 종류");
    }

    @Test
    @DisplayName("create에서 name이 null/blank이면 IllegalArgumentException이 발생한다")
    void create에서_name이_null이거나_blank이면_IllegalArgumentException이_발생한다() {
        assertThatThrownBy(() -> Group.create(GroupKind.PUBLIC, null, "설명", "thumb", 10))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Group.create(GroupKind.PUBLIC, "   ", "설명", "thumb", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("그룹 이름");
    }

    @Test
    @DisplayName("create에서 description이 null/blank이면 IllegalArgumentException이 발생한다")
    void create에서_description이_null이거나_blank이면_IllegalArgumentException이_발생한다() {
        assertThatThrownBy(() -> Group.create(GroupKind.PUBLIC, "여행모임", null, "thumb", 10))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Group.create(GroupKind.PUBLIC, "여행모임", "   ", "thumb", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("그룹 설명");
    }

    @Test
    @DisplayName("create에서  memberLimit이 1 미만 또는 50 초과면 IllegalArgumentException이 발생한다")
    void create에서_memberLimit이_1미만_또는_50초과면_IllegalArgumentException이_발생한다() {
        assertThatThrownBy(() -> Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("그룹 멤버 제한");

        assertThatThrownBy(() -> Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("그룹 멤버 제한");
    }

    @Test
    @DisplayName("addMember에서 멤버를 추가하면 group.userGroups와 user.userGroups에 UserGroup이 함께 추가된다")
    void addMember에서_멤버를_추가하면_group과_user에_UserGroup이_함께_추가된다() {
        // given
        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        User user = User.builder()
                .providerId("kakao-1")
                .nickname("상윤")
                .email("test@test.com")
                .profileUrl("http://img")
                .build();

        // when
        group.addMember(user, Role.OWNER);

        // then
        assertThat(group.getUserGroups()).hasSize(1);
        assertThat(user.getUserGroups()).hasSize(1);

        var ugFromGroup = group.getUserGroups().get(0);
        var ugFromUser = user.getUserGroups().get(0);

        assertThat(ugFromGroup).isSameAs(ugFromUser);
        assertThat(ugFromGroup.getGroup()).isSameAs(group);
        assertThat(ugFromGroup.getUser()).isSameAs(user);
        assertThat(ugFromGroup.getRole()).isEqualTo(Role.OWNER);
        assertThat(ugFromGroup.getJoinedAt()).isNotNull();
        assertThat(ugFromGroup.getLeftAt()).isNull();
    }
}
