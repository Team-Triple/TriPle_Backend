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
import org.triple.backend.group.entity.joinApply.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.JoinApplyJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class JoinApplyIntegrationTest {

    private static final String USER_SESSION_KEY = "USER_ID";
    private static final String CSRF_TOKEN = "csrf-token";

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
                        .sessionAttr(CsrfTokenManager.CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        JoinApply savedApply = joinApplyJpaRepository.findByGroupIdAndUserId(group.getId(), applicant.getId()).orElseThrow();
        assertThat(savedApply.getJoinStatus()).isEqualTo(JoinStatus.PENDING);
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
                        .sessionAttr(CsrfTokenManager.CSRF_TOKEN_KEY, CSRF_TOKEN)
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
                        .sessionAttr(CsrfTokenManager.CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        JoinApply reapplied = joinApplyJpaRepository.findByGroupIdAndUserId(group.getId(), applicant.getId()).orElseThrow();
        assertThat(reapplied.getJoinStatus()).isEqualTo(JoinStatus.PENDING);
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
                        .sessionAttr(CsrfTokenManager.CSRF_TOKEN_KEY, CSRF_TOKEN)
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
                        .sessionAttr(CsrfTokenManager.CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isConflict());
    }
}
