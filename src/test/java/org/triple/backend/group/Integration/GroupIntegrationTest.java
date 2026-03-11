package org.triple.backend.group.Integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.auth.session.UuidCrypto;
import org.triple.backend.common.DbCleaner;
import org.triple.backend.common.annotation.IntegrationTest;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.JoinApplyJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.TravelReview;
import org.triple.backend.travel.entity.TravelReviewImage;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.TravelReviewImageJpaRepository;
import org.triple.backend.travel.repository.TravelReviewJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN_KEY;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;
import static org.triple.backend.group.fixture.GroupFixtures.privateGroup;
import static org.triple.backend.group.fixture.GroupFixtures.publicGroup;

@IntegrationTest
public class GroupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private UserGroupJpaRepository userGroupJpaRepository;

    @Autowired
    private JoinApplyJpaRepository joinApplyJpaRepository;

    @Autowired
    private TravelItineraryJpaRepository travelItineraryJpaRepository;

    @Autowired
    private TravelReviewJpaRepository travelReviewJpaRepository;

    @Autowired
    private TravelReviewImageJpaRepository travelReviewImageJpaRepository;

    @Autowired
    private DbCleaner dbCleaner;

    @Autowired
    private UuidCrypto uuidCrypto;

    @BeforeEach
    void setUp() {
        dbCleaner.clean();
    }

    @Test
    @DisplayName("로그인 세션이 있으면 그룹 생성이 되고, 생성자는 OWNER로 UserGroup이 저장된다")
    void 로그인_세션이_있으면_그룹_생성이_되고_생성자는_OWNER로_UserGroup이_저장된다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-1")
                        .nickname("상윤")
                        .email("test@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        String body = """
                {
                  "name": "여행모임",
                  "description": "3월 일본 여행",
                  "memberLimit": 10,
                  "groupKind": "PUBLIC",
                  "thumbNailUrl": "https://example.com/thumb.png"
                }
                """;

        // when
        var result = mockMvc.perform(post("/groups")
                        .sessionAttr(USER_SESSION_KEY, owner.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").exists())
                .andReturn();

        // then
        String response = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        long groupId = root.get("groupId").asLong();

        Group savedGroup = groupJpaRepository.findById(groupId).orElseThrow();
        assertThat(savedGroup.getName()).isEqualTo("여행모임");
        assertThat(savedGroup.getMemberLimit()).isEqualTo(10);
        assertThat(savedGroup.getGroupKind()).isEqualTo(GroupKind.PUBLIC);

        // then
        List<UserGroup> userGroups = userGroupJpaRepository.findAll();
        assertThat(userGroups).hasSize(1);
        UserGroup userGroup = userGroups.get(0);

        assertThat(userGroup.getUser().getId()).isEqualTo(owner.getId());
        assertThat(userGroup.getGroup().getId()).isEqualTo(savedGroup.getId());
        assertThat(userGroup.getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    @DisplayName("공개 그룹 목록 첫 페이지를 조회하면 PUBLIC만 size개 조회된다")
    void 공개_그룹_목록_첫_페이지_조회하면_PUBLIC만_size개_조회된다() throws Exception {
        // given
        for (int i = 1; i <= 12; i++) {
            groupJpaRepository.save(publicGroup("public-" + i));
        }

        for (int i = 1; i <= 3; i++) {
            groupJpaRepository.save(privateGroup("private-" + i));
        }

        // when & then
        mockMvc.perform(get("/groups")
                        .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isNumber())
                .andExpect(jsonPath("$.items[*].name", everyItem(startsWith("public-"))));
    }

    @Test
    @DisplayName("공개 그룹 목록 다음 페이지를 조회하면 cursor 기준으로 조회된다")
    void 공개_그룹_목록_다음_페이지_조회하면_cursor_기준으로_조회된다() throws Exception {
        // given
        for (int i = 1; i <= 15; i++) groupJpaRepository.save(publicGroup("public-" + i));

        String firstJson = mockMvc.perform(get("/groups").param("size", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GroupCursorResponseDto first = objectMapper.readValue(firstJson, GroupCursorResponseDto.class);
        Long nextCursor = first.nextCursor();

        // when & then
        mockMvc.perform(get("/groups")
                        .param("cursor", String.valueOf(nextCursor))
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(result -> {
                    String json = result.getResponse().getContentAsString();
                    GroupCursorResponseDto dto = objectMapper.readValue(json, GroupCursorResponseDto.class);
                    assertThat(dto.items())
                            .allSatisfy(item -> assertThat(item.groupId()).isLessThan(nextCursor));
                });
    }

    @Test
    @DisplayName("로그인 사용자는 내가 속한 그룹 목록을 조회할 수 있다")
    void 로그인_사용자는_내가_속한_그룹_목록을_조회할_수_있다() throws Exception {
        // given
        User me = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-my-groups")
                        .nickname("상윤")
                        .email("my-groups@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        User other = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-my-groups-other")
                        .nickname("민규")
                        .email("my-groups-other@test.com")
                        .profileUrl("http://img2")
                        .build()
        );

        for (int i = 1; i <= 12; i++) {
            Group group = groupJpaRepository.saveAndFlush(Group.create(GroupKind.PUBLIC, "my-group-" + i, "설명", "thumb", 10));
            userGroupJpaRepository.saveAndFlush(UserGroup.create(me, group, Role.MEMBER));
        }

        Group otherGroup = groupJpaRepository.saveAndFlush(Group.create(GroupKind.PUBLIC, "other-group", "설명", "thumb", 10));
        userGroupJpaRepository.saveAndFlush(UserGroup.create(other, otherGroup, Role.MEMBER));

        // when & then
        mockMvc.perform(get("/groups/me")
                        .param("size", "10")
                        .sessionAttr(USER_SESSION_KEY, me.getPublicUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isNumber())
                .andExpect(jsonPath("$.items[*].name", everyItem(startsWith("my-group-"))))
                .andExpect(jsonPath("$.items[*].name", not(hasItem("other-group"))));
    }

    @Test
    @DisplayName("내 그룹 조회 시 PUBLIC과 PRIVATE 그룹이 모두 반환된다")
    void 내_그룹_조회_시_PUBLIC과_PRIVATE_그룹이_모두_반환된다() throws Exception {
        // given
        User me = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-my-groups-kind")
                        .nickname("상윤")
                        .email("my-groups-kind@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group publicGroup = groupJpaRepository.saveAndFlush(
                Group.create(GroupKind.PUBLIC, "public-my-group", "설명", "thumb", 10)
        );
        Group privateGroup = groupJpaRepository.saveAndFlush(
                Group.create(GroupKind.PRIVATE, "private-my-group", "설명", "thumb", 10)
        );
        userGroupJpaRepository.saveAndFlush(UserGroup.create(me, publicGroup, Role.MEMBER));
        userGroupJpaRepository.saveAndFlush(UserGroup.create(me, privateGroup, Role.MEMBER));

        // when
        String json = mockMvc.perform(get("/groups/me")
                        .param("size", "10")
                        .sessionAttr(USER_SESSION_KEY, me.getPublicUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("public-my-group", "private-my-group")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then
        GroupCursorResponseDto response = objectMapper.readValue(json, GroupCursorResponseDto.class);
        List<Long> ids = response.items().stream().map(GroupCursorResponseDto.GroupSummaryDto::groupId).toList();
        List<Group> groups = groupJpaRepository.findAllById(ids);

        assertThat(groups).extracting(Group::getGroupKind)
                .containsExactlyInAnyOrder(GroupKind.PUBLIC, GroupKind.PRIVATE);
    }

    @Test
    @DisplayName("내 그룹 목록 다음 페이지는 cursor 기준으로 중복 없이 이어진다")
    void 내_그룹_목록_다음_페이지는_cursor_기준으로_중복_없이_이어진다() throws Exception {
        // given
        User me = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-my-groups-next")
                        .nickname("상윤")
                        .email("my-groups-next@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        for (int i = 1; i <= 12; i++) {
            Group group = groupJpaRepository.saveAndFlush(Group.create(GroupKind.PUBLIC, "my-next-" + i, "설명", "thumb", 10));
            userGroupJpaRepository.saveAndFlush(UserGroup.create(me, group, Role.MEMBER));
        }

        // when
        String firstJson = mockMvc.perform(get("/groups/me")
                        .param("size", "5")
                        .sessionAttr(USER_SESSION_KEY, me.getPublicUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        GroupCursorResponseDto first = objectMapper.readValue(firstJson, GroupCursorResponseDto.class);
        Long firstNextCursor = first.nextCursor();
        List<Long> firstIds = first.items().stream().map(GroupCursorResponseDto.GroupSummaryDto::groupId).toList();

        String secondJson = mockMvc.perform(get("/groups/me")
                        .param("cursor", String.valueOf(firstNextCursor))
                        .param("size", "5")
                        .sessionAttr(USER_SESSION_KEY, me.getPublicUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        GroupCursorResponseDto second = objectMapper.readValue(secondJson, GroupCursorResponseDto.class);
        List<Long> secondIds = second.items().stream().map(GroupCursorResponseDto.GroupSummaryDto::groupId).toList();

        // then
        assertThat(secondIds).allSatisfy(id -> assertThat(id).isLessThan(firstNextCursor));
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
    }

    @Test
    @DisplayName("비로그인 사용자가 내 그룹 목록을 조회하면 401을 반환한다")
    void 비로그인_사용자가_내_그룹_목록을_조회하면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/groups/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비로그인 사용자는 공개 그룹 메뉴 정보를 조회할 수 있다")
    void 비로그인_사용자는_공개_그룹_메뉴_정보를_조회할_수_있다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-menu-public")
                        .nickname("상윤")
                        .email("owner-menu-public@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "즐거운 여행단", "MBTI P들의 모임입니다. 맛집 탐방!", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/menu", savedGroup.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("즐거운 여행단"))
                .andExpect(jsonPath("$.description").value("MBTI P들의 모임입니다. 맛집 탐방!"))
                .andExpect(jsonPath("$.currentMemberCount").value(1))
                .andExpect(jsonPath("$.memberLimit").value(10))
                .andExpect(jsonPath("$.thumbNailUrl").value("https://example.com/thumb.png"))
                .andExpect(jsonPath("$.role").value(Role.GUEST.toString()));
    }

    @Test
    @DisplayName("로그인한 JOINED 멤버는 그룹 메뉴 조회 시 자신의 역할을 반환받는다")
    void 로그인한_JOINED_멤버는_그룹_메뉴_조회_시_자신의_역할을_반환받는다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-menu-private")
                        .nickname("상윤")
                        .email("owner-menu-private@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User member = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-member-menu-private")
                        .nickname("민규")
                        .email("member-menu-private@test.com")
                        .profileUrl("http://img2")
                        .build()
        );

        Group group = Group.create(GroupKind.PRIVATE, "비공개모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addCurrentMemberCount();
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/menu", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getPublicUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("비공개모임"))
                .andExpect(jsonPath("$.currentMemberCount").value(2))
                .andExpect(jsonPath("$.thumbNailUrl").value("https://example.com/thumb.png"))
                .andExpect(jsonPath("$.role").value(Role.MEMBER.toString()));
    }

    @Test
    @DisplayName("비공개 그룹 메뉴 조회 시 멤버가 아니면 403을 반환한다")
    void 비공개_그룹_메뉴_조회_시_멤버가_아니면_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-menu-private-forbidden")
                        .nickname("상윤")
                        .email("owner-menu-private-forbidden@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PRIVATE, "비공개모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/menu", savedGroup.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹을 조회할 권한이 없습니다."));
    }

    @Test
    @DisplayName("그룹 멤버 목록 조회 시 JOINED 상태 사용자만 반환된다")
    void 그룹_멤버_목록_조회_시_JOINED_상태_사용자만_반환된다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-group-users")
                        .nickname("상윤")
                        .email("owner-group-users@test.com")
                        .profileUrl("http://img-owner")
                        .build()
        );
        User joinedMember = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-joined-member-group-users")
                        .nickname("민규")
                        .email("joined-member-group-users@test.com")
                        .profileUrl("http://img-member")
                        .build()
        );
        User leftMember = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-left-member-group-users")
                        .nickname("태호")
                        .email("left-member-group-users@test.com")
                        .profileUrl("http://img-left")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "멤버목록모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        userGroupJpaRepository.saveAndFlush(UserGroup.create(joinedMember, savedGroup, Role.MEMBER));

        UserGroup leftUserGroup = UserGroup.create(leftMember, savedGroup, Role.MEMBER);
        leftUserGroup.leave();
        userGroupJpaRepository.saveAndFlush(leftUserGroup);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/users", savedGroup.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(2)))
                .andExpect(jsonPath("$.users[*].id", not(hasItem(owner.getPublicUuid().toString()))))
                .andExpect(jsonPath("$.users[*].id", not(hasItem(joinedMember.getPublicUuid().toString()))))
                .andExpect(jsonPath("$.users[*].name", containsInAnyOrder("상윤", "민규")))
                .andExpect(jsonPath("$.users[?(@.name == '태호')]", hasSize(0)))
                .andExpect(jsonPath("$.users[?(@.isOwner == true)]", hasSize(1)));
    }

    @Test
    @DisplayName("비공개 그룹 멤버 목록 조회는 로그인 멤버라도 403을 반환한다")
    void 비공개_그룹_멤버_목록_조회는_로그인_멤버라도_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-group-users-private")
                        .nickname("상윤")
                        .email("owner-group-users-private@test.com")
                        .profileUrl("http://img-owner")
                        .build()
        );
        User member = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-member-group-users-private")
                        .nickname("민규")
                        .email("member-group-users-private@test.com")
                        .profileUrl("http://img-member")
                        .build()
        );

        Group group = Group.create(GroupKind.PRIVATE, "비공개멤버목록모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addCurrentMemberCount();
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/users", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getPublicUuid()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("PRIVATE 그룹 멤버 목록은 조회할 수 없습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 그룹의 멤버 목록 조회 시 404를 반환한다")
    void 존재하지_않는_그룹의_멤버_목록_조회_시_404를_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/groups/{groupId}/users", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 그룹 입니다."));
    }

    @Test
    @DisplayName("비로그인 사용자는 공개 그룹 상세 정보를 조회할 수 있다")
    void 비로그인_사용자는_공개_그룹_상세_정보를_조회할_수_있다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-detail")
                        .nickname("상윤")
                        .email("owner-detail@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "3월 일본 여행", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(get("/groups/{groupId}", savedGroup.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("여행모임"))
                .andExpect(jsonPath("$.description").value("3월 일본 여행"))
                .andExpect(jsonPath("$.groupKind").value("PUBLIC"))
                .andExpect(jsonPath("$.thumbNailUrl").value("https://example.com/thumb.png"))
                .andExpect(jsonPath("$.currentMemberCount").value(1))
                .andExpect(jsonPath("$.memberLimit").value(10))
                .andExpect(jsonPath("$.role").value(Role.GUEST.toString()))
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.users[0].name").value("상윤"))
                .andExpect(jsonPath("$.users[0].isOwner").value(true))
                .andExpect(jsonPath("$.recentPhotos", hasSize(0)))
                .andExpect(jsonPath("$.recentTravels", hasSize(0)))
                .andExpect(jsonPath("$.recentReviews", hasSize(0)));
    }

    @Test
    @DisplayName("비공개 그룹 상세 조회 시 멤버가 아니면 403을 반환한다")
    void 비공개_그룹_상세_조회_시_멤버가_아니면_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-private-detail")
                        .nickname("상윤")
                        .email("owner-private-detail@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        User outsider = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-outsider-private-detail")
                        .nickname("민규")
                        .email("outsider-private-detail@test.com")
                        .profileUrl("http://img2")
                        .build()
        );

        Group group = Group.create(GroupKind.PRIVATE, "비공개모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(get("/groups/{groupId}", savedGroup.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹을 조회할 권한이 없습니다."));
    }

    @Test
    @DisplayName("키워드 길이가 20자를 초과하면 400을 반환한다")
    void 키워드_길이가_20자를_초과하면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/groups")
                        .param("keyword", "aaaaaaaaaaaaaaaaaaaaa")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("검색어는 최대 20자까지 입력할 수 있습니다."));
    }

    @Test
    @DisplayName("비공개 그룹 상세 조회 시 멤버는 200을 반환한다")
    void 비공개_그룹_상세_조회_시_멤버는_200을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-private-member-detail")
                        .nickname("상윤")
                        .email("owner-private-member-detail@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        User member = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-member-private-member-detail")
                        .nickname("민규")
                        .email("member-private-member-detail@test.com")
                        .profileUrl("http://img2")
                        .build()
        );

        Group group = Group.create(GroupKind.PRIVATE, "비공개모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(get("/groups/{groupId}", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getPublicUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("비공개모임"))
                .andExpect(jsonPath("$.groupKind").value("PRIVATE"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.users", hasSize(2)))
                .andExpect(jsonPath("$.users[*].name", containsInAnyOrder("상윤", "민규")))
                .andExpect(jsonPath("$.users[?(@.isOwner == true)]", hasSize(1)))
                .andExpect(jsonPath("$.recentPhotos", hasSize(0)))
                .andExpect(jsonPath("$.recentTravels", hasSize(0)))
                .andExpect(jsonPath("$.recentReviews", hasSize(0)));
    }

    @Test
    @DisplayName("그룹 상세 조회 시 최근 여행/리뷰/사진 세부 항목이 함께 반환된다")
    void 그룹_상세_조회_시_최근_여행_리뷰_사진_세부_항목이_함께_반환된다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-detail-items")
                        .nickname("상윤")
                        .email("owner-detail-items@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User member = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-member-detail-items")
                        .nickname("민규")
                        .email("member-detail-items@test.com")
                        .profileUrl("http://img2")
                        .build()
        );
        Group group = Group.create(GroupKind.PUBLIC, "상세모임", "상세설명", "https://example.com/detail-thumb.png", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addCurrentMemberCount();
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        TravelItinerary itinerary = travelItineraryJpaRepository.saveAndFlush(
                new TravelItinerary(
                        "봄 제주 여행",
                        LocalDateTime.of(2026, 4, 10, 10, 0),
                        LocalDateTime.of(2026, 4, 12, 18, 0),
                        savedGroup,
                        "일정 설명",
                        1,
                        false
                )
        );

        Group otherGroup = Group.create(GroupKind.PUBLIC, "다른모임", "다른설명", "https://example.com/other-thumb.png", 10);
        otherGroup.addMember(member, Role.OWNER);
        Group savedOtherGroup = groupJpaRepository.saveAndFlush(otherGroup);

        TravelItinerary otherGroupItinerary = travelItineraryJpaRepository.saveAndFlush(
                new TravelItinerary(
                        "부산 여행",
                        LocalDateTime.of(2026, 5, 1, 10, 0),
                        LocalDateTime.of(2026, 5, 2, 18, 0),
                        savedOtherGroup,
                        "다른 그룹 일정",
                        1,
                        false
                )
        );

        TravelReview ownerReview = travelReviewJpaRepository.saveAndFlush(
                createTravelReview(owner, itinerary, "오너 후기", false)
        );
        TravelReview memberReview = travelReviewJpaRepository.saveAndFlush(
                createTravelReview(member, itinerary, "멤버 후기", false)
        );
        TravelReview memberOtherGroupReview = travelReviewJpaRepository.saveAndFlush(
                createTravelReview(member, otherGroupItinerary, "타 그룹 후기", false)
        );
        TravelReview deletedReview = travelReviewJpaRepository.saveAndFlush(
                createTravelReview(member, itinerary, "삭제된 후기", true)
        );

        TravelReviewImage ownerImage = travelReviewImageJpaRepository.saveAndFlush(
                createTravelReviewImage(owner, ownerReview, "https://img/owner.png")
        );
        TravelReviewImage memberImage = travelReviewImageJpaRepository.saveAndFlush(
                createTravelReviewImage(member, memberReview, "https://img/member.png")
        );
        travelReviewImageJpaRepository.saveAndFlush(
                createTravelReviewImage(member, memberOtherGroupReview, "https://img/other-group.png")
        );
        travelReviewImageJpaRepository.saveAndFlush(
                createTravelReviewImage(member, deletedReview, "https://img/deleted.png")
        );

        // when & then
        mockMvc.perform(get("/groups/{groupId}", savedGroup.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentTravels", hasSize(1)))
                .andExpect(jsonPath("$.recentTravels[0].travelItineraryId").value(itinerary.getId().intValue()))
                .andExpect(jsonPath("$.recentTravels[0].title").value("봄 제주 여행"))
                .andExpect(jsonPath("$.recentTravels[0].description").value("일정 설명"))
                .andExpect(jsonPath("$.recentTravels[0].memberCount").value(1))
                .andExpect(jsonPath("$.recentTravels[0].startAt").value("2026-04-10T10:00:00"))
                .andExpect(jsonPath("$.recentTravels[0].endAt").value("2026-04-12T18:00:00"))
                .andExpect(jsonPath("$.recentReviews", hasSize(2)))
                .andExpect(jsonPath("$.recentReviews[*].reviewId", containsInAnyOrder(ownerReview.getId().intValue(), memberReview.getId().intValue())))
                .andExpect(jsonPath("$.recentReviews[*].travelItineraryName", everyItem(is("봄 제주 여행"))))
                .andExpect(jsonPath("$.recentReviews[*].content", containsInAnyOrder("오너 후기", "멤버 후기")))
                .andExpect(jsonPath("$.recentReviews[*].writerNickname", containsInAnyOrder("상윤", "민규")))
                .andExpect(jsonPath("$.recentReviews[*].imageUrl", containsInAnyOrder("https://img/owner.png", "https://img/member.png")))
                .andExpect(jsonPath("$.recentReviews[*].content", not(hasItem("타 그룹 후기"))))
                .andExpect(jsonPath("$.recentReviews[*].content", not(hasItem("삭제된 후기"))))
                .andExpect(jsonPath("$.recentReviews[*].imageUrl", not(hasItem("https://img/other-group.png"))))
                .andExpect(jsonPath("$.recentReviews[*].imageUrl", not(hasItem("https://img/deleted.png"))))
                .andExpect(jsonPath("$.recentPhotos", hasSize(2)))
                .andExpect(jsonPath("$.recentPhotos[*].imageId", containsInAnyOrder(ownerImage.getId().intValue(), memberImage.getId().intValue())))
                .andExpect(jsonPath("$.recentPhotos[*].imageUrl", containsInAnyOrder("https://img/owner.png", "https://img/member.png")))
                .andExpect(jsonPath("$.recentPhotos[*].imageUrl", not(hasItem("https://img/other-group.png"))))
                .andExpect(jsonPath("$.recentPhotos[*].imageUrl", not(hasItem("https://img/deleted.png"))))
                .andExpect(jsonPath("$.users[?(@.isOwner == true)]", hasSize(1)));
    }

    @Test
    @DisplayName("로그인 세션이 있으면 그룹 삭제가 되고, 연관된 UserGroup도 함께 삭제된다")
    void 로그인_세션이_있으면_그룹_삭제가_되고_연관된_UserGroup도_함께_삭제된다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner")
                        .nickname("상윤")
                        .email("owner@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);

        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        assertThat(groupJpaRepository.findByIdAndIsDeletedFalse(savedGroup.getId())).isPresent();
        assertThat(userGroupJpaRepository.findAll()).hasSize(1);

        // when
        mockMvc.perform(delete("/groups/{groupId}", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, owner.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        // then
        assertThat(groupJpaRepository.findByIdAndIsDeletedFalse(savedGroup.getId())).isEmpty();
        assertThat(groupJpaRepository.findById(savedGroup.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(userGroupJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("그룹에 다른 JOINED 멤버가 남아있으면 그룹 삭제 요청 시 409를 반환한다")
    void 그룹에_다른_JOINED_멤버가_남아있으면_그룹_삭제_요청_시_409를_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-delete-blocked")
                        .nickname("상윤")
                        .email("owner-delete-blocked@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        User member = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-member-delete-blocked")
                        .nickname("민규")
                        .email("member-delete-blocked@test.com")
                        .profileUrl("http://img2")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addCurrentMemberCount();
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(delete("/groups/{groupId}", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, owner.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("그룹에 다른 멤버가 있어 삭제할 수 없습니다."));

        Group notDeleted = groupJpaRepository.findById(savedGroup.getId()).orElseThrow();
        assertThat(notDeleted.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("로그인한 멤버는 그룹을 탈퇴할 수 있다")
    void 로그인한_멤버는_그룹을_탈퇴할_수_있다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-leave")
                        .nickname("상윤")
                        .email("owner-leave@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        User member = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-member-leave")
                        .nickname("민규")
                        .email("member-leave@test.com")
                        .profileUrl("http://img2")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addCurrentMemberCount();
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        JoinApply approvedApply = JoinApply.create(member, savedGroup);
        approvedApply.approve();
        joinApplyJpaRepository.saveAndFlush(approvedApply);

        // when & then
        mockMvc.perform(delete("/groups/{groupId}/users/me", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        UserGroup leftUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), member.getId()).orElseThrow();
        Group updatedGroup = groupJpaRepository.findById(savedGroup.getId()).orElseThrow();

        assertThat(leftUserGroup.getJoinStatus()).isEqualTo(JoinStatus.LEFTED);
        assertThat(leftUserGroup.getLeftAt()).isNotNull();
        assertThat(updatedGroup.getCurrentMemberCount()).isEqualTo(1);
        assertThat(joinApplyJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), member.getId())).isEmpty();
    }

    @Test
    @DisplayName("그룹 주인이 탈퇴를 요청하면 403을 반환한다")
    void 그룹_주인이_탈퇴를_요청하면_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-cannot-leave")
                        .nickname("상윤")
                        .email("owner-cannot-leave@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(delete("/groups/{groupId}/users/me", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, owner.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("그룹 주인은 탈퇴할 수 없습니다."));
    }

    @Test
    @DisplayName("이미 탈퇴한 사용자가 탈퇴를 다시 요청하면 403을 반환한다")
    void 이미_탈퇴한_사용자가_탈퇴를_다시_요청하면_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-already-left")
                        .nickname("상윤")
                        .email("owner-already-left@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        User member = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-member-already-left")
                        .nickname("민규")
                        .email("member-already-left@test.com")
                        .profileUrl("http://img2")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addCurrentMemberCount();
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        mockMvc.perform(delete("/groups/{groupId}/users/me", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        // when & then
        mockMvc.perform(delete("/groups/{groupId}/users/me", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이미 탈퇴한 그룹입니다."));
    }

    @Test
    @DisplayName("그룹 멤버가 아닌 사용자가 탈퇴를 요청하면 403을 반환한다")
    void 그룹_멤버가_아닌_사용자가_탈퇴를_요청하면_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-not-member-leave")
                        .nickname("상윤")
                        .email("owner-not-member-leave@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        User outsider = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-outsider-not-member-leave")
                        .nickname("민규")
                        .email("outsider-not-member-leave@test.com")
                        .profileUrl("http://img2")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(delete("/groups/{groupId}/users/me", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, outsider.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹을 조회할 권한이 없습니다."));
    }

    @Test
    @DisplayName("로그인한 소유자는 그룹 정보를 수정할 수 있고 DB에도 반영된다")
    void 로그인한_소유자는_그룹_정보를_수정할_수_있고_DB에도_반영된다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-update")
                        .nickname("상윤")
                        .email("owner-update@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "기존모임", "기존설명", "https://example.com/old.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        String body = """
                {
                  "groupKind": "PRIVATE",
                  "name": "수정모임",
                  "description": "수정설명",
                  "thumbNailUrl": "https://example.com/new.png",
                  "memberLimit": 20
                }
                """;

        // when & then
        mockMvc.perform(patch("/groups/{groupId}", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, owner.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(savedGroup.getId()))
                .andExpect(jsonPath("$.groupKind").value("PRIVATE"))
                .andExpect(jsonPath("$.name").value("수정모임"))
                .andExpect(jsonPath("$.description").value("수정설명"))
                .andExpect(jsonPath("$.thumbNailUrl").value("https://example.com/new.png"))
                .andExpect(jsonPath("$.memberLimit").value(20));

        Group updated = groupJpaRepository.findById(savedGroup.getId()).orElseThrow();
        assertThat(updated.getGroupKind()).isEqualTo(GroupKind.PRIVATE);
        assertThat(updated.getName()).isEqualTo("수정모임");
        assertThat(updated.getDescription()).isEqualTo("수정설명");
        assertThat(updated.getThumbNailUrl()).isEqualTo("https://example.com/new.png");
        assertThat(updated.getMemberLimit()).isEqualTo(20);
    }

    @Test
    @DisplayName("그룹 소유권 이전 요청 시 소유자와 대상의 역할이 교체된다")
    void 그룹_소유권_이전_요청_시_소유자와_대상의_역할이_교체된다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-transfer")
                        .nickname("상윤")
                        .email("owner-transfer@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User target = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-target-transfer")
                        .nickname("민규")
                        .email("target-transfer@test.com")
                        .profileUrl("http://img2")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(target, Role.MEMBER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(patch("/groups/{groupId}/owner/{targetUserId}", savedGroup.getId(), encryptedUserId(target))
                        .sessionAttr(USER_SESSION_KEY, owner.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        UserGroup ownerUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), owner.getId()).orElseThrow();
        UserGroup targetUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(savedGroup.getId(), target.getId()).orElseThrow();

        assertThat(ownerUserGroup.getRole()).isEqualTo(Role.MEMBER);
        assertThat(targetUserGroup.getRole()).isEqualTo(Role.OWNER);
        assertThat(ownerUserGroup.getJoinStatus()).isEqualTo(JoinStatus.JOINED);
        assertThat(targetUserGroup.getJoinStatus()).isEqualTo(JoinStatus.JOINED);
    }

    @Test
    @DisplayName("그룹 소유자가 자기 자신에게 소유권 이전 요청 시 403을 반환한다")
    void 그룹_소유자가_자기_자신에게_소유권_이전_요청_시_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-self-transfer")
                        .nickname("상윤")
                        .email("owner-self-transfer@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(patch("/groups/{groupId}/owner/{targetUserId}", savedGroup.getId(), encryptedUserId(owner))
                        .sessionAttr(USER_SESSION_KEY, owner.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("그룹 주인은 스스로를 강등시킬 수 없습니다."));
    }

    @Test
    @DisplayName("그룹 소유자가 아닌 사용자가 소유권 이전 요청 시 403을 반환한다")
    void 그룹_소유자가_아닌_사용자가_소유권_이전_요청_시_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-transfer-forbidden")
                        .nickname("상윤")
                        .email("owner-transfer-forbidden@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User member = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-member-transfer-forbidden")
                        .nickname("민규")
                        .email("member-transfer-forbidden@test.com")
                        .profileUrl("http://img2")
                        .build()
        );
        User target = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-target-transfer-forbidden")
                        .nickname("지호")
                        .email("target-transfer-forbidden@test.com")
                        .profileUrl("http://img3")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        group.addMember(member, Role.MEMBER);
        group.addMember(target, Role.MEMBER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(patch("/groups/{groupId}/owner/{targetUserId}", savedGroup.getId(), encryptedUserId(target))
                        .sessionAttr(USER_SESSION_KEY, member.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("그룹 수정/삭제 권한이 없습니다."));
    }

    @Test
    @DisplayName("소유권 이전 대상이 그룹 멤버가 아니면 403을 반환한다")
    void 소유권_이전_대상이_그룹_멤버가_아니면_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-transfer-target")
                        .nickname("상윤")
                        .email("owner-transfer-target@test.com")
                        .profileUrl("http://img")
                        .build()
        );
        User outsider = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-outsider-transfer-target")
                        .nickname("민규")
                        .email("outsider-transfer-target@test.com")
                        .profileUrl("http://img2")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        mockMvc.perform(patch("/groups/{groupId}/owner/{targetUserId}", savedGroup.getId(), encryptedUserId(outsider))
                        .sessionAttr(USER_SESSION_KEY, owner.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹을 조회할 권한이 없습니다."));
    }

    @Test
    @DisplayName("그룹 소유자가 아닌 사용자가 수정하면 403을 반환한다")
    void 그룹_소유자가_아닌_사용자가_수정하면_403을_반환한다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner-update")
                        .nickname("상윤")
                        .email("owner-update@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        User otherUser = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-other-update")
                        .nickname("민규")
                        .email("other-update@test.com")
                        .profileUrl("http://img2")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "기존모임", "기존설명", "https://example.com/old.png", 10);
        group.addMember(owner, Role.OWNER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        String body = """
                {
                  "groupKind": "PRIVATE",
                  "name": "수정모임",
                  "description": "수정설명",
                  "thumbNailUrl": "https://example.com/new.png",
                  "memberLimit": 20
                }
                """;

        // when & then
        mockMvc.perform(patch("/groups/{groupId}", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, otherUser.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    private TravelReview createTravelReview(User user, TravelItinerary travelItinerary, String content, boolean isDeleted) {
        TravelReview travelReview = new TravelReview();
        ReflectionTestUtils.setField(travelReview, "user", user);
        ReflectionTestUtils.setField(travelReview, "travelItinerary", travelItinerary);
        ReflectionTestUtils.setField(travelReview, "content", content);
        ReflectionTestUtils.setField(travelReview, "isDeleted", isDeleted);
        ReflectionTestUtils.setField(travelReview, "view", 0);
        return travelReview;
    }

    private TravelReviewImage createTravelReviewImage(User user, TravelReview review, String imageUrl) {
        TravelReviewImage travelReviewImage = new TravelReviewImage();
        ReflectionTestUtils.setField(travelReviewImage, "user", user);
        ReflectionTestUtils.setField(travelReviewImage, "travelReview", review);
        ReflectionTestUtils.setField(travelReviewImage, "reviewImageUrl", imageUrl);
        return travelReviewImage;
    }

    private String encryptedUserId(final User user) {
        return uuidCrypto.encrypt(user.getPublicUuid());
    }
}
