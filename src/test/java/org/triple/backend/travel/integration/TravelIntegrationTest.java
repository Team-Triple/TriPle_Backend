package org.triple.backend.travel.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.common.DbCleaner;
import org.triple.backend.common.annotation.IntegrationTest;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.auth.session.CsrfTokenManager.CSRF_HEADER;
import static org.triple.backend.auth.session.CsrfTokenManager.CSRF_TOKEN_KEY;

@IntegrationTest
class TravelIntegrationTest {

    private static final String USER_SESSION_KEY = "USER_ID";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private UserGroupJpaRepository userGroupJpaRepository;

    @Autowired
    private TravelItineraryJpaRepository travelItineraryJpaRepository;

    @Autowired
    private UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;

    @Autowired
    private DbCleaner dbCleaner;

    @BeforeEach
    void setUp() {
        dbCleaner.clean();
    }

    @Test
    @DisplayName("로그인한 그룹원은 여행을 생성한다.")
    void 권한있는_그룹원_여행_생성_가능() throws Exception {
        //given
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());
        userGroupJpaRepository.save(createUserGroup(user, group));

        String body = """
                {
                  "title": "제목",
                  "startAt": "2026-02-14T00:00:00",
                  "endAt": "2026-02-16T00:00:00",
                  "groupId": %d,
                  "description": "설명"
                }
                """.formatted(group.getId());

        mockMvc.perform(post("/travels")
                        .sessionAttr(USER_SESSION_KEY, user.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, "test-token")
                        .header(CSRF_HEADER, "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itineraryId").isNumber());

        assertThat(travelItineraryJpaRepository.count()).isEqualTo(1);
        assertThat(userTravelItineraryJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("로그인하지 않으면 401을 반환한다.")
    void 여행_생성_권한_확인() throws Exception {
        String body = """
                {
                  "title": "제목",
                  "startAt": "2026-02-14T00:00:00",
                  "endAt": "2026-02-16T00:00:00",
                  "groupId": 1,
                  "description": "설명"
                }
                """;

        mockMvc.perform(post("/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("그룹원이 아니면 403을 반환한다.")
    void 여행_생성_권한_확인2() throws Exception {
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());

        String body = """
                {
                  "title": "제목",
                  "startAt": "2026-02-14T00:00:00",
                  "endAt": "2026-02-16T00:00:00",
                  "groupId": %d,
                  "description": "설명"
                }
                """.formatted(group.getId());

        mockMvc.perform(post("/travels")
                        .sessionAttr(USER_SESSION_KEY, user.getPublicUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("여행 일정 삭제 시 소프트 삭제된다.")
    void 여행_일정_삭제_성공() throws Exception {
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());

        TravelItinerary travelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                1,
                false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(user, travelItinerary, UserRole.LEADER));

        mockMvc.perform(delete("/travels/{travelId}", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, user.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, "test-token")
                        .header(CSRF_HEADER, "test-token"))
                .andExpect(status().isOk());

        TravelItinerary deleted = travelItineraryJpaRepository.findById(travelItinerary.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("내 여행 일정 목록 조회에 성공한다.")
    void 내_여행_일정_목록_조회_성공() throws Exception {
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());
        userGroupJpaRepository.save(createUserGroup(user, group));

        TravelItinerary travelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                1,
                false
        ));

        mockMvc.perform(get("/travels/{groupId}", group.getId())
                        .sessionAttr(USER_SESSION_KEY, user.getPublicUuid())
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("title"))
                .andExpect(jsonPath("$.items[0].description").value("description"))
                .andExpect(jsonPath("$.items[0].memberCount").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("비로그인 사용자는 여행 목록 조회 시 count만 반환받는다.")
    void 비로그인_여행_목록_조회_count만_반환() throws Exception {
        Group group = groupJpaRepository.save(createGroup());

        travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                1,
                false
        ));

        mockMvc.perform(get("/travels/{groupId}", group.getId())
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("로그인 비멤버 사용자는 여행 목록 조회 시 count만 반환받는다.")
    void 로그인_비멤버_여행_목록_조회_count만_반환() throws Exception {
        User leader = userJpaRepository.save(createUser());
        User outsider = userJpaRepository.save(createUserWithProviderId("kakao-outsider"));
        Group group = groupJpaRepository.save(createGroup());
        userGroupJpaRepository.save(createUserGroup(leader, group));

        travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                1,
                false
        ));

        mockMvc.perform(get("/travels/{groupId}", group.getId())
                        .sessionAttr(USER_SESSION_KEY, outsider.getPublicUuid())
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("여행 멤버가 탈퇴 요청하면 참가 정보가 삭제된다.")
    void 여행_멤버가_탈퇴_요청하면_참가_정보가_삭제된다() throws Exception {
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());

        TravelItinerary travelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                2,
                false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(user, travelItinerary, UserRole.MEMBER));

        mockMvc.perform(delete("/travels/{travelId}/users/me", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, user.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, "test-token")
                        .header(CSRF_HEADER, "test-token"))
                .andExpect(status().isOk());

        assertThat(userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(user.getId(), travelItinerary.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("여행 리더가 탈퇴 요청하면 401을 반환한다.")
    void 여행_리더가_탈퇴_요청하면_401을_반환한다() throws Exception {
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());

        TravelItinerary travelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                1,
                false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(user, travelItinerary, UserRole.LEADER));

        mockMvc.perform(delete("/travels/{travelId}/users/me", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, user.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, "test-token")
                        .header(CSRF_HEADER, "test-token"))
                .andExpect(status().isUnauthorized());

        assertThat(userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(user.getId(), travelItinerary.getId()))
                .isPresent();
    }

    @Test
    @DisplayName("여행 참가 요청 성공 시 멤버십이 저장되고 인원이 증가한다.")
    void 여행_참가_요청_성공() throws Exception {
        User leader = userJpaRepository.save(createUser());
        User joiner = userJpaRepository.save(createUserWithProviderId("kakao-2"));
        Group group = groupJpaRepository.save(createGroup());
        userGroupJpaRepository.save(createUserGroup(leader, group));
        userGroupJpaRepository.save(createUserGroup(joiner, group));

        TravelItinerary travelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                1,
                false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(leader, travelItinerary, UserRole.LEADER));

        mockMvc.perform(post("/travels/{travelId}/users/me", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, joiner.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, "test-token")
                        .header(CSRF_HEADER, "test-token"))
                .andExpect(status().isOk());

        TravelItinerary updated = travelItineraryJpaRepository.findById(travelItinerary.getId()).orElseThrow();
        assertThat(updated.getMemberCount()).isEqualTo(2);
        assertThat(userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(joiner.getId(), travelItinerary.getId()))
                .isPresent();
    }

    @Test
    @DisplayName("이미 참가한 유저가 다시 참가 요청하면 409를 반환한다.")
    void 이미_참가한_유저가_다시_참가_요청하면_409를_반환한다() throws Exception {
        User leader = userJpaRepository.save(createUser());
        User joiner = userJpaRepository.save(createUserWithProviderId("kakao-3"));
        Group group = groupJpaRepository.save(createGroup());
        userGroupJpaRepository.save(createUserGroup(leader, group));
        userGroupJpaRepository.save(createUserGroup(joiner, group));

        TravelItinerary travelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                2,
                false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(leader, travelItinerary, UserRole.LEADER));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(joiner, travelItinerary, UserRole.MEMBER));

        mockMvc.perform(post("/travels/{travelId}/users/me", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, joiner.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, "test-token")
                        .header(CSRF_HEADER, "test-token"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("그룹 멤버가 아니면 여행 참가 요청 시 403을 반환한다.")
    void 그룹_멤버가_아니면_여행_참가_요청_시_403을_반환한다() throws Exception {
        User leader = userJpaRepository.save(createUser());
        User outsider = userJpaRepository.save(createUserWithProviderId("kakao-4"));
        Group group = groupJpaRepository.save(createGroup());
        userGroupJpaRepository.save(createUserGroup(leader, group));

        TravelItinerary travelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                1,
                false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(leader, travelItinerary, UserRole.LEADER));

        mockMvc.perform(post("/travels/{travelId}/users/me", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, outsider.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, "test-token")
                        .header(CSRF_HEADER, "test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("여행 참가 요청 시 정원 제한 없이 성공한다.")
    void 여행_참가_요청_정원제한없음_성공() throws Exception {
        User leader = userJpaRepository.save(createUser());
        User joiner = userJpaRepository.save(createUserWithProviderId("kakao-5"));
        Group group = groupJpaRepository.save(createGroup());
        userGroupJpaRepository.save(createUserGroup(leader, group));
        userGroupJpaRepository.save(createUserGroup(joiner, group));

        TravelItinerary travelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                1,
                false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(leader, travelItinerary, UserRole.LEADER));

        mockMvc.perform(post("/travels/{travelId}/users/me", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, joiner.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, "test-token")
                        .header(CSRF_HEADER, "test-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("여행 메타 정보 조회에 성공한다.")
    void 여행_메타_정보_조회_성공() throws Exception {
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());

        TravelItinerary travelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "제주도 여행",
                LocalDateTime.of(2026, 3, 1, 0, 0),
                LocalDateTime.of(2026, 3, 5, 0, 0),
                group, "설명", 1, false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(user, travelItinerary, UserRole.LEADER));

        mockMvc.perform(get("/travels/{travelId}/info", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, user.getPublicUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제주도 여행"))
                .andExpect(jsonPath("$.startAt").value("2026-03-01T00:00:00"))
                .andExpect(jsonPath("$.endAt").value("2026-03-05T00:00:00"))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].nickname").value("nick"))
                .andExpect(jsonPath("$.members[0].userRole").value("LEADER"));
    }

    @Test
    @DisplayName("비로그인 사용자가 여행 메타 정보 조회 시 401을 반환한다.")
    void 비로그인_사용자_여행_메타_정보_조회_401() throws Exception {
        mockMvc.perform(get("/travels/{travelId}/info", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("여행 멤버가 아닌 사용자가 여행 메타 정보 조회 시 404를 반환한다.")
    void 여행_멤버가_아닌_사용자_여행_메타_정보_조회_404() throws Exception {
        User outsider = userJpaRepository.save(createUserWithProviderId("kakao-outsider"));
        User leader = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());

        TravelItinerary travelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "제주도 여행",
                LocalDateTime.of(2026, 3, 1, 0, 0),
                LocalDateTime.of(2026, 3, 5, 0, 0),
                group, "설명", 1, false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(leader, travelItinerary, UserRole.LEADER));

        mockMvc.perform(get("/travels/{travelId}/info", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, outsider.getPublicUuid()))
                .andExpect(status().isNotFound());
    }

    private User createUser() {
        return User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId("kakao-1")
                .nickname("nick")
                .email("test@test.com")
                .profileUrl("http://img")
                .build();
    }

    private User createUserWithProviderId(String providerId) {
        return User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId(providerId)
                .nickname("nick-" + providerId)
                .email(providerId + "@test.com")
                .profileUrl("http://img")
                .build();
    }

    private Group createGroup() {
        return Group.create(
                GroupKind.PUBLIC,
                "모임",
                "설명",
                "http://thumb",
                10
        );
    }

    private UserGroup createUserGroup(User user, Group group) {
        return UserGroup.builder()
                .user(user)
                .group(group)
                .role(Role.MEMBER)
                .joinStatus(JoinStatus.JOINED)
                .joinedAt(LocalDateTime.now())
                .build();
    }
}

