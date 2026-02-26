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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                  "description": "설명",
                  "thumbnailUrl": "test-url",
                  "memberLimit": 5
                }
                """.formatted(group.getId());

        mockMvc.perform(post("/travels")
                        .sessionAttr(USER_SESSION_KEY, user.getId())
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
                  "description": "설명",
                  "thumbnailUrl": "test-url",
                  "memberLimit": 5
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
                  "description": "설명",
                  "thumbnailUrl": "test-url",
                  "memberLimit": 5
                }
                """.formatted(group.getId());

        mockMvc.perform(post("/travels")
                        .sessionAttr(USER_SESSION_KEY, user.getId())
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
                "test-url",
                5,
                1,
                false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(user, travelItinerary, UserRole.LEADER));

        mockMvc.perform(delete("/travels/{travelId}", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, user.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, "test-token")
                        .header(CSRF_HEADER, "test-token"))
                .andExpect(status().isOk());

        TravelItinerary deleted = travelItineraryJpaRepository.findById(travelItinerary.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
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
                "test-url",
                5,
                2,
                false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(user, travelItinerary, UserRole.MEMBER));

        mockMvc.perform(delete("/travels/{travelId}/users/me", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, user.getId())
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
                "test-url",
                5,
                1,
                false
        ));
        userTravelItineraryJpaRepository.save(UserTravelItinerary.of(user, travelItinerary, UserRole.LEADER));

        mockMvc.perform(delete("/travels/{travelId}/users/me", travelItinerary.getId())
                        .sessionAttr(USER_SESSION_KEY, user.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, "test-token")
                        .header(CSRF_HEADER, "test-token"))
                .andExpect(status().isUnauthorized());

        assertThat(userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(user.getId(), travelItinerary.getId()))
                .isPresent();
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
