package org.triple.backend.group.Integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.common.DbCleaner;
import org.triple.backend.common.annotation.IntegrationTest;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.joinApply.JoinApplyStatus;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.JoinApplyJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN_KEY;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@IntegrationTest
public class JoinApplyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DbCleaner dbCleaner;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private JoinApplyJpaRepository joinApplyJpaRepository;

    @Autowired
    private UserGroupJpaRepository userGroupJpaRepository;

    @BeforeEach
    void setUp() {
        dbCleaner.clean();
    }

    @Test
    @DisplayName("로그인 사용자는 그룹 가입 신청을 할 수 있다")
    void 로그인_사용자는_그룹_가입_신청을_할_수_있다() throws Exception {
        // given
        User applicant = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-applicant")
                        .nickname("지원자")
                        .email("applicant@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = groupJpaRepository.save(
                Group.create(GroupKind.PUBLIC, "여행모임", "설명", "https://example.com/thumb.png", 10)
        );

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies", group.getId())
                        .sessionAttr(USER_SESSION_KEY, applicant.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        JoinApply savedApply = joinApplyJpaRepository.findByGroupIdAndUserId(group.getId(), applicant.getId()).orElseThrow();
        assertThat(savedApply.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.PENDING);
    }

    @Test
    @DisplayName("이미 가입 신청한 그룹에 다시 신청하면 409를 반환한다")
    void 이미_가입_신청한_그룹에_다시_신청하면_409를_반환한다() throws Exception {
        // given
        User applicant = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-applicant")
                        .nickname("지원자")
                        .email("applicant@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = groupJpaRepository.save(
                Group.create(GroupKind.PUBLIC, "여행모임", "설명", "https://example.com/thumb.png", 10)
        );

        joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, group));

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies", group.getId())
                        .sessionAttr(USER_SESSION_KEY, applicant.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("취소된 가입 신청은 재신청할 수 있다")
    void 취소된_가입_신청은_재신청할_수_있다() throws Exception {
        // given
        User applicant = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-applicant")
                        .nickname("지원자")
                        .email("applicant@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = groupJpaRepository.save(
                Group.create(GroupKind.PUBLIC, "여행모임", "설명", "https://example.com/thumb.png", 10)
        );

        JoinApply joinApply = joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, group));
        joinApply.cancel();
        joinApplyJpaRepository.saveAndFlush(joinApply);

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies", group.getId())
                        .sessionAttr(USER_SESSION_KEY, applicant.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        JoinApply reapplied = joinApplyJpaRepository.findByGroupIdAndUserId(group.getId(), applicant.getId()).orElseThrow();
        assertThat(reapplied.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.PENDING);
    }

    @Test
    @DisplayName("거절된 가입 신청은 재신청 시 409를 반환한다")
    void 거절된_가입_신청은_재신청_시_409를_반환한다() throws Exception {
        // given
        User applicant = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-applicant")
                        .nickname("지원자")
                        .email("applicant@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = groupJpaRepository.save(
                Group.create(GroupKind.PUBLIC, "여행모임", "설명", "https://example.com/thumb.png", 10)
        );

        JoinApply joinApply = joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, group));
        joinApply.reject();
        joinApplyJpaRepository.saveAndFlush(joinApply);

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies", group.getId())
                        .sessionAttr(USER_SESSION_KEY, applicant.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("이미 그룹 멤버인 경우 가입 신청하면 409를 반환한다")
    void 이미_그룹_멤버인_경우_가입_신청하면_409를_반환한다() throws Exception {
        // given
        User member = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-member")
                        .nickname("멤버")
                        .email("member@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(member, Role.MEMBER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("오너는 그룹 가입 신청을 승인할 수 있다")
    void 오너는_그룹_가입_신청을_승인할_수_있다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner")
                        .nickname("오너")
                        .email("owner@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User applicant = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-applicant")
                        .nickname("지원자")
                        .email("applicant@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        Group group = Group.create(GroupKind.PUBLIC, "승인모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);
        JoinApply joinApply = joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, savedGroup));

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}", savedGroup.getId(), joinApply.getId())
                        .sessionAttr(USER_SESSION_KEY, owner.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        JoinApply approvedApply = joinApplyJpaRepository.findById(joinApply.getId()).orElseThrow();
        Group updatedGroup = groupJpaRepository.findById(savedGroup.getId()).orElseThrow();
        assertThat(approvedApply.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.APPROVED);
        assertThat(approvedApply.getApprovedAt()).isNotNull();
        assertThat(userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(savedGroup.getId(), applicant.getId(), JoinStatus.JOINED))
                .isTrue();
        assertThat(updatedGroup.getCurrentMemberCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 사용자가 가입 신청을 승인하면 404를 반환한다")
    void 존재하지_않는_사용자가_가입_신청을_승인하면_404를_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-missing")
                        .nickname("오너")
                        .email("owner-missing@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User applicant = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-applicant-missing")
                        .nickname("지원자")
                        .email("applicant-missing@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "유저검증모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);
        JoinApply joinApply = joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, savedGroup));

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}", savedGroup.getId(), joinApply.getId())
                        .sessionAttr(USER_SESSION_KEY, 999999L)
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isNotFound());

        JoinApply pendingApply = joinApplyJpaRepository.findById(joinApply.getId()).orElseThrow();
        assertThat(pendingApply.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.PENDING);
        assertThat(userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(savedGroup.getId(), applicant.getId(), JoinStatus.JOINED))
                .isFalse();
    }

    @Test
    @DisplayName("오너가 아닌 사용자가 가입 신청을 승인하면 403을 반환한다")
    void 오너가_아닌_사용자가_가입_신청을_승인하면_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner")
                        .nickname("오너")
                        .email("owner@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User outsider = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-outsider")
                        .nickname("외부인")
                        .email("outsider@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User applicant = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-applicant")
                        .nickname("지원자")
                        .email("applicant@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        Group group = Group.create(GroupKind.PUBLIC, "권한모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);
        JoinApply joinApply = joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, savedGroup));

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}", savedGroup.getId(), joinApply.getId())
                        .sessionAttr(USER_SESSION_KEY, outsider.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isForbidden());

        JoinApply pendingApply = joinApplyJpaRepository.findById(joinApply.getId()).orElseThrow();
        assertThat(pendingApply.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.PENDING);
        assertThat(userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(savedGroup.getId(), applicant.getId(), JoinStatus.JOINED))
                .isFalse();
    }

    @Test
    @DisplayName("LEFTED 이력이 있는 사용자는 가입 승인 시 재가입 처리된다")
    void LEFTED_이력이_있는_사용자는_가입_승인_시_재가입_처리된다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-rejoin")
                        .nickname("오너")
                        .email("owner-rejoin@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User applicant = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-applicant-rejoin")
                        .nickname("지원자")
                        .email("applicant-rejoin@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "재가입모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        userGroupJpaRepository.saveAndFlush(UserGroup.builder()
                .user(applicant)
                .group(savedGroup)
                .role(Role.MEMBER)
                .joinStatus(JoinStatus.LEFTED)
                .joinedAt(LocalDateTime.now().minusDays(1))
                .leftAt(LocalDateTime.now())
                .build());

        JoinApply joinApply = joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, savedGroup));

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}", savedGroup.getId(), joinApply.getId())
                        .sessionAttr(USER_SESSION_KEY, owner.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        JoinApply approvedApply = joinApplyJpaRepository.findById(joinApply.getId()).orElseThrow();
        Group updatedGroup = groupJpaRepository.findById(savedGroup.getId()).orElseThrow();
        UserGroup rejoinedUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), applicant.getId())
                .orElseThrow();

        assertThat(approvedApply.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.APPROVED);
        assertThat(userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(savedGroup.getId(), applicant.getId(), JoinStatus.JOINED))
                .isTrue();
        assertThat(rejoinedUserGroup.getLeftAt()).isNull();
        assertThat(userGroupJpaRepository.count()).isEqualTo(2);
        assertThat(updatedGroup.getCurrentMemberCount()).isEqualTo(2);
    }
}
