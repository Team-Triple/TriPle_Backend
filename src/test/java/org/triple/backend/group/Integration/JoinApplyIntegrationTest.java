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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    @DisplayName("오너는 상태 조건으로 가입 신청 사용자 목록을 커서 조회할 수 있다")
    void 오너는_상태_조건으로_가입_신청_사용자_목록을_커서_조회할_수_있다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-list-cursor")
                        .nickname("오너")
                        .email("owner-list-cursor@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        Group group = Group.create(GroupKind.PUBLIC, "조회커서모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        for (int i = 1; i <= 5; i++) {
            User applicant = userJpaRepository.save(
                    User.builder()
                            .providerId("kakao-list-cursor-applicant-" + i)
                            .nickname("지원자" + i)
                            .email("list-cursor-applicant-" + i + "@test.com")
                            .profileUrl("http://img")
                            .build()
            );
            joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, savedGroup));
        }

        // when
        String firstJson = mockMvc.perform(get("/groups/{groupId}/join-applies", savedGroup.getId())
                        .param("status", "PENDING")
                        .param("size", "2")
                        .sessionAttr(USER_SESSION_KEY, owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(2)))
                .andExpect(jsonPath("$.users[0].joinApplyId").isNumber())
                .andExpect(jsonPath("$.users[*].status", everyItem(is("PENDING"))))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstRoot = objectMapper.readTree(firstJson);
        long nextCursor = firstRoot.get("nextCursor").asLong();
        Set<String> firstNicknames = Set.of(
                firstRoot.get("users").get(0).get("nickname").asText(),
                firstRoot.get("users").get(1).get("nickname").asText()
        );

        String secondJson = mockMvc.perform(get("/groups/{groupId}/join-applies", savedGroup.getId())
                        .param("status", "PENDING")
                        .param("cursor", String.valueOf(nextCursor))
                        .param("size", "2")
                        .sessionAttr(USER_SESSION_KEY, owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(2)))
                .andExpect(jsonPath("$.users[0].joinApplyId").isNumber())
                .andExpect(jsonPath("$.users[*].status", everyItem(is("PENDING"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode secondRoot = objectMapper.readTree(secondJson);
        Set<String> secondNicknames = Set.of(
                secondRoot.get("users").get(0).get("nickname").asText(),
                secondRoot.get("users").get(1).get("nickname").asText()
        );

        // then
        assertThat(secondNicknames).doesNotContainAnyElementsOf(firstNicknames);
    }

    @Test
    @DisplayName("status 미입력 시 전체 상태의 가입 신청 사용자 목록을 조회한다")
    void status_미입력_시_전체_상태의_가입_신청_사용자_목록을_조회한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-list-all")
                        .nickname("오너")
                        .email("owner-list-all@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        Group group = Group.create(GroupKind.PUBLIC, "전체조회모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        User pendingUser = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-list-all-pending")
                        .nickname("대기지원자")
                        .email("list-all-pending@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User approvedUser = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-list-all-approved")
                        .nickname("승인지원자")
                        .email("list-all-approved@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User canceledUser = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-list-all-canceled")
                        .nickname("취소지원자")
                        .email("list-all-canceled@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        joinApplyJpaRepository.saveAndFlush(JoinApply.create(pendingUser, savedGroup));
        JoinApply approved = joinApplyJpaRepository.saveAndFlush(JoinApply.create(approvedUser, savedGroup));
        approved.approve();
        joinApplyJpaRepository.saveAndFlush(approved);
        JoinApply canceled = joinApplyJpaRepository.saveAndFlush(JoinApply.create(canceledUser, savedGroup));
        canceled.cancel();
        joinApplyJpaRepository.saveAndFlush(canceled);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/join-applies", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(3)))
                .andExpect(jsonPath("$.users[0].joinApplyId").isNumber())
                .andExpect(jsonPath("$.users[*].status", hasItems("PENDING", "APPROVED", "CANCELED")))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("오너가 아닌 사용자가 가입 신청 사용자 목록을 조회하면 403을 반환한다")
    void 오너가_아닌_사용자가_가입_신청_사용자_목록을_조회하면_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-list-forbidden")
                        .nickname("오너")
                        .email("owner-list-forbidden@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User member = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-member-list-forbidden")
                        .nickname("멤버")
                        .email("member-list-forbidden@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User applicant = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-applicant-list-forbidden")
                        .nickname("지원자")
                        .email("applicant-list-forbidden@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "권한조회모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);
        joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, savedGroup));

        // when & then
        mockMvc.perform(get("/groups/{groupId}/join-applies", savedGroup.getId())
                        .param("status", "PENDING")
                        .sessionAttr(USER_SESSION_KEY, member.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비로그인 사용자가 가입 신청 사용자 목록을 조회하면 401을 반환한다")
    void 비로그인_사용자가_가입_신청_사용자_목록을_조회하면_401을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-list-unauthorized")
                        .nickname("오너")
                        .email("owner-list-unauthorized@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        Group group = Group.create(GroupKind.PUBLIC, "비로그인조회모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/join-applies", savedGroup.getId())
                        .param("status", "PENDING"))
                .andExpect(status().isUnauthorized());
    }
}
