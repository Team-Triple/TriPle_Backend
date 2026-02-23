package org.triple.backend.group.unit.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.joinApply.JoinApplyStatus;
import org.triple.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JoinApplyTest {

    @Test
    @DisplayName("create로 생성하면 PENDING 상태와 연관 엔티티가 설정된다")
    void create로_생성하면_PENDING_상태와_연관_엔티티가_설정된다() {
        // given
        User user = createUser("kakao-1", "user1@test.com");
        Group group = createGroup("모임-1");

        // when
        JoinApply joinApply = JoinApply.create(user, group);

        // then
        assertThat(joinApply.getUser()).isSameAs(user);
        assertThat(joinApply.getGroup()).isSameAs(group);
        assertThat(joinApply.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.PENDING);
        assertThat(joinApply.getApprovedAt()).isNull();
        assertThat(joinApply.getRejectedAt()).isNull();
        assertThat(joinApply.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("cancel 하면 상태가 CANCELED가 되고 canceledAt이 기록된다")
    void cancel_하면_상태가_CANCELED가_되고_canceledAt이_기록된다() {
        // given
        JoinApply joinApply = JoinApply.create(createUser("kakao-2", "user2@test.com"), createGroup("모임-2"));

        // when
        joinApply.cancel();

        // then
        assertThat(joinApply.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.CANCELED);
        assertThat(joinApply.getCanceledAt()).isNotNull();
        assertThat(joinApply.isCanceled()).isTrue();
    }

    @Test
    @DisplayName("reject 하면 상태가 REJECTED가 되고 rejectedAt이 기록된다")
    void reject_하면_상태가_REJECTED가_되고_rejectedAt이_기록된다() {
        // given
        JoinApply joinApply = JoinApply.create(createUser("kakao-3", "user3@test.com"), createGroup("모임-3"));

        // when
        joinApply.reject();

        // then
        assertThat(joinApply.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.REJECTED);
        assertThat(joinApply.getRejectedAt()).isNotNull();
        assertThat(joinApply.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("취소된 신청은 reapply 시 PENDING으로 변경되고 시간 필드가 초기화된다")
    void 취소된_신청은_reapply_시_PENDING으로_변경되고_시간_필드가_초기화된다() {
        // given
        JoinApply joinApply = JoinApply.create(createUser("kakao-4", "user4@test.com"), createGroup("모임-4"));
        joinApply.reject();
        joinApply.cancel();
        assertThat(joinApply.getRejectedAt()).isNotNull();
        assertThat(joinApply.getCanceledAt()).isNotNull();

        // when
        joinApply.reapply();

        // then
        assertThat(joinApply.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.PENDING);
        assertThat(joinApply.getApprovedAt()).isNull();
        assertThat(joinApply.getRejectedAt()).isNull();
        assertThat(joinApply.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("취소 상태가 아니면 reapply 시 IllegalStateException이 발생한다")
    void 취소_상태가_아니면_reapply_시_IllegalStateException이_발생한다() {
        // given
        JoinApply joinApply = JoinApply.create(createUser("kakao-5", "user5@test.com"), createGroup("모임-5"));

        // when & then
        assertThatThrownBy(joinApply::reapply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("취소된 신청만 재신청");
    }

    private static User createUser(final String providerId, final String email) {
        return User.builder()
                .providerId(providerId)
                .nickname("테스트유저")
                .email(email)
                .profileUrl("http://img")
                .build();
    }

    private static Group createGroup(final String name) {
        return Group.create(GroupKind.PUBLIC, name, "설명", "thumb", 10);
    }
}
