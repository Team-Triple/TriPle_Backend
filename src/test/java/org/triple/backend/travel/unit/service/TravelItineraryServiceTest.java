package org.triple.backend.travel.unit.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.travel.dto.request.TravelItineraryUpdateRequestDto;
import org.triple.backend.travel.dto.response.TravelItineraryCursorResponseDto;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.exception.TravelItineraryErrorCode;
import org.triple.backend.travel.dto.request.TravelItinerarySaveRequestDto;
import org.triple.backend.travel.dto.response.TravelItinerarySaveResponseDto;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.exception.UserTravelItineraryErrorCode;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.travel.service.TravelItineraryService;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

import java.time.LocalDateTime;

@ServiceTest
@Import({TravelItineraryService.class})
class TravelItineraryServiceTest {
    @Autowired
    private TravelItineraryService travelItineraryService;

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

    @Test
    @DisplayName("여행 저장 시 유저를 찾을 수 없으면 예외를 던진다.")
    void 여행_저장_유저_없음_예외() {
        // given
        Long invalidUserId = 1L;
        TravelItinerarySaveRequestDto request = new TravelItinerarySaveRequestDto(
                "제목",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                1L,
                "설명",
                "test-url",
                5
        );

        // when & then
        Assertions.assertThatThrownBy(() -> travelItineraryService.saveTravels(request, invalidUserId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("여행 저장 시 그룹을 찾을 수 없으면 예외를 던진다.")
    void 여행_저장_그룹_없음_예외() {
        // given
        User user = userJpaRepository.save(createUser());
        TravelItinerarySaveRequestDto request = new TravelItinerarySaveRequestDto(
                "제목",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                1L,
                "설명",
                "test-url",
                5
        );

        // when & then
        Assertions.assertThatThrownBy(() -> travelItineraryService.saveTravels(request, user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    @DisplayName("여행 저장 시 그룹 멤버가 아니면 권한 예외를 던진다.")
    void 여행_저장_그룹_멤버_아님_예외() {
        // given
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());
        TravelItinerarySaveRequestDto request = new TravelItinerarySaveRequestDto(
                "제목",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group.getId(),
                "설명",
                "test-url",
                5
        );

        // when & then
        Assertions.assertThatThrownBy(() -> travelItineraryService.saveTravels(request, user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TravelItineraryErrorCode.SAVE_FORBIDDEN);
    }

    @Test
    @DisplayName("여행 저장 요청을 정상 처리한다.")
    void 여행_저장_성공() {
        // given
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());
        userGroupJpaRepository.save(createUserGroup(user, group));

        TravelItinerarySaveRequestDto request = new TravelItinerarySaveRequestDto(
                "제목",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group.getId(),
                "설명",
                "test-url",
                5
        );

        // when
        TravelItinerarySaveResponseDto response = travelItineraryService.saveTravels(request, user.getId());

        // then
        TravelItinerary saved = travelItineraryJpaRepository.findById(response.itineraryId()).orElse(null);
        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(saved).isNotNull();
        Assertions.assertThat(saved)
                .extracting("title", "startAt", "endAt", "description", "thumbnailUrl", "memberLimit")
                .containsExactly(
                        "제목",
                        LocalDateTime.of(2026, 2, 14, 0, 0),
                        LocalDateTime.of(2026, 2, 16, 0, 0),
                        "설명",
                        "test-url",
                        5
                );
    }

    @Test
    @DisplayName("여행 업데이트 시 TravelItinerary가 없으면 예외를 던진다.(해당 여행이 없음)")
    void TravelItinerary_없으면_예외() {
        //given
        Long nullTravelKey = 1L;

        //when & then
        Assertions.assertThatThrownBy(() -> travelItineraryService.updateTravel(null, nullTravelKey, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TravelItineraryErrorCode.TRAVEL_NOT_FOUND);
    }

    @Test
    @DisplayName("여행 업데이트 시 userTravelItinerary가 없으면 예외를 던진다.(해당 여행 멤버가 아님)")
    void UserTravelItinerary_없으면_예외() {
        //given
        Group group = groupJpaRepository.save(createGroup());
        TravelItinerary travelItinerary = new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                "test-thumbnailUrl",
                20,
                1,
                false);
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.save(travelItinerary);
        User user = userJpaRepository.save(createUser());

        //when & then
        Assertions.assertThatThrownBy(() -> travelItineraryService.updateTravel(null, savedTravelItinerary.getId(), user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND);
    }

    @Test
    @DisplayName("여행 업데이트 시 해당 유저의 여행 내 role이 LEADER가 아니면 예외를 던진다.(리더만 수정 가능)")
    void TravelItinerary_수정_LEADER_만_가능() {
        //given
        Group group = groupJpaRepository.save(createGroup());
        TravelItinerary travelItinerary = new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                "test-thumbnailUrl",
                20,
                1,
                false);
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.save(travelItinerary);
        User savedUser = userJpaRepository.save(createUser());
        UserTravelItinerary userTravelItinerary = new UserTravelItinerary(savedUser, savedTravelItinerary, UserRole.MEMBER);
        UserTravelItinerary savedUserTravelItinerary = userTravelItineraryJpaRepository.save(userTravelItinerary);

        //when & then
        Assertions.assertThatThrownBy(() -> travelItineraryService.updateTravel(null, savedTravelItinerary.getId(), savedUser.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserTravelItineraryErrorCode.UPDATE_UNAUTHORIZED);
    }

    @Test
    @DisplayName("여행 업데이트 시 정상적으로 업데이트가 된다.")
    void 업데이트_정상_수행() {
        //given
        Group group = groupJpaRepository.save(createGroup());
        TravelItinerary travelItinerary = new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                "test-thumbnailUrl",
                20,
                1,
                false);
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.save(travelItinerary);
        User savedUser = userJpaRepository.save(createUser());
        UserTravelItinerary userTravelItinerary = new UserTravelItinerary(savedUser, travelItinerary, UserRole.LEADER);
        userTravelItineraryJpaRepository.save(userTravelItinerary);

        //when
        travelItineraryService.updateTravel(new TravelItineraryUpdateRequestDto(
                "test", null,null,null,null,null), savedTravelItinerary.getId(), savedUser.getId());
        TravelItinerary searchedTravelItinerary = travelItineraryJpaRepository.findById(savedTravelItinerary.getId()).get();

        //then
        Assertions.assertThat(searchedTravelItinerary).isNotNull()
                .extracting("title", "startAt", "endAt", "description", "thumbnailUrl", "memberLimit")
        .containsExactly("test", savedTravelItinerary.getStartAt(), savedTravelItinerary.getEndAt(), savedTravelItinerary.getDescription(), savedTravelItinerary.getThumbnailUrl(), savedTravelItinerary.getMemberLimit());
    }

    @Test
    @DisplayName("삭제 대상 여행 일정이 없으면 예외를 던진다.")
    void 삭제_대상_여행_일정_없음_예외() {
        Assertions.assertThatThrownBy(() -> travelItineraryService.deleteTravel(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TravelItineraryErrorCode.TRAVEL_NOT_FOUND);
    }

    @Test
    @DisplayName("유저-여행 일정 매핑이 없으면 예외를 던진다.")
    void 유저_여행일정_매핑_없음_예외() {
        Group group = groupJpaRepository.save(createGroup());
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                "test-thumbnailUrl",
                20,
                1,
                false));
        User user = userJpaRepository.save(createUser());

        Assertions.assertThatThrownBy(() -> travelItineraryService.deleteTravel(savedTravelItinerary.getId(), user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND);
    }

    @Test
    @DisplayName("리더가 아니면 삭제 권한 예외를 던진다.")
    void 리더_아님_삭제권한_예외() {
        Group group = groupJpaRepository.save(createGroup());
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                "test-thumbnailUrl",
                20,
                1,
                false));
        User user = userJpaRepository.save(createUser());
        userTravelItineraryJpaRepository.save(new UserTravelItinerary(user, savedTravelItinerary, UserRole.MEMBER));

        Assertions.assertThatThrownBy(() -> travelItineraryService.deleteTravel(savedTravelItinerary.getId(), user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserTravelItineraryErrorCode.DELETE_UNAUTHORIZED);
    }

    @Test
    @DisplayName("여행 일정 삭제 시 삭제 상태로 변경된다.")
    void 여행_일정_삭제_성공() {
        Group group = groupJpaRepository.save(createGroup());
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                "test-thumbnailUrl",
                20,
                1,
                false));
        User user = userJpaRepository.save(createUser());
        userTravelItineraryJpaRepository.save(new UserTravelItinerary(user, savedTravelItinerary, UserRole.LEADER));

        travelItineraryService.deleteTravel(savedTravelItinerary.getId(), user.getId());

        TravelItinerary deletedTravel = travelItineraryJpaRepository.findById(savedTravelItinerary.getId()).orElseThrow();
        Assertions.assertThat(deletedTravel.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("여행 목록 조회 시 유저를 찾을 수 없으면 예외를 던진다.")
    void 여행_목록_조회_유저_없음_예외() {
        Assertions.assertThatThrownBy(() -> travelItineraryService.browseTravels(1L, null, 10, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.NOT_GROUP_MEMBER);
    }

    @Test
    @DisplayName("여행 목록 조회 시 그룹을 찾을 수 없으면 예외를 던진다.")
    void 여행_목록_조회_그룹_없음_예외() {
        User user = userJpaRepository.save(createUser());

        Assertions.assertThatThrownBy(() -> travelItineraryService.browseTravels(1L, null, 10, user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.NOT_GROUP_MEMBER);
    }

    @Test
    @DisplayName("여행 목록 조회 시 그룹 멤버가 아니면 예외를 던진다.")
    void 여행_목록_조회_그룹_멤버_아님_예외() {
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());

        Assertions.assertThatThrownBy(() -> travelItineraryService.browseTravels(group.getId(), null, 10, user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.NOT_GROUP_MEMBER);
    }

    @Test
    @DisplayName("여행 목록 조회를 커서 기반으로 정상 처리한다.")
    void 여행_목록_조회_커서_성공() {
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());
        userGroupJpaRepository.save(createUserGroup(user, group));

        Group anotherGroup = groupJpaRepository.save(Group.create(GroupKind.PUBLIC, "다른모임", "설명", "http://thumb2", 10));

        TravelItinerary firstTravel = travelItineraryJpaRepository.save(new TravelItinerary(
                "첫번째",
                LocalDateTime.of(2026, 3, 1, 0, 0),
                LocalDateTime.of(2026, 3, 2, 0, 0),
                group,
                "설명1",
                "thumb-1",
                5,
                1,
                false
        ));

        TravelItinerary secondTravel = travelItineraryJpaRepository.save(new TravelItinerary(
                "두번째",
                LocalDateTime.of(2026, 3, 3, 0, 0),
                LocalDateTime.of(2026, 3, 4, 0, 0),
                group,
                "설명2",
                "thumb-2",
                6,
                2,
                false
        ));

        TravelItinerary thirdTravel = travelItineraryJpaRepository.save(new TravelItinerary(
                "세번째",
                LocalDateTime.of(2026, 3, 5, 0, 0),
                LocalDateTime.of(2026, 3, 6, 0, 0),
                group,
                "설명3",
                "thumb-3",
                7,
                3,
                false
        ));

        travelItineraryJpaRepository.save(new TravelItinerary(
                "다른그룹",
                LocalDateTime.of(2026, 3, 7, 0, 0),
                LocalDateTime.of(2026, 3, 8, 0, 0),
                anotherGroup,
                "설명4",
                "thumb-4",
                8,
                1,
                false
        ));

        travelItineraryJpaRepository.save(new TravelItinerary(
                "삭제된여행",
                LocalDateTime.of(2026, 3, 9, 0, 0),
                LocalDateTime.of(2026, 3, 10, 0, 0),
                group,
                "설명5",
                "thumb-5",
                9,
                1,
                true
        ));

        TravelItineraryCursorResponseDto firstPage = travelItineraryService.browseTravels(group.getId(), null, 2, user.getId());
        Assertions.assertThat(firstPage.items()).hasSize(2);
        Assertions.assertThat(firstPage.hasNext()).isTrue();
        Assertions.assertThat(firstPage.nextCursor()).isEqualTo(secondTravel.getId());
        Assertions.assertThat(firstPage.items())
                .extracting(item -> item.title())
                .containsExactly(thirdTravel.getTitle(), secondTravel.getTitle());
        Assertions.assertThat(firstPage.items().get(0))
                .extracting("description", "thumbnailUrl", "memberCount", "memberLimit")
                .containsExactly("설명3", "thumb-3", 3, 7);

        TravelItineraryCursorResponseDto secondPage = travelItineraryService.browseTravels(group.getId(), firstPage.nextCursor(), 2, user.getId());
        Assertions.assertThat(secondPage.items()).hasSize(1);
        Assertions.assertThat(secondPage.hasNext()).isFalse();
        Assertions.assertThat(secondPage.nextCursor()).isNull();
        Assertions.assertThat(secondPage.items().get(0).title()).isEqualTo(firstTravel.getTitle());
    }

    private static Group createGroup() {
        return Group.create(GroupKind.PUBLIC, "모임", "설명", "http://thumb", 10);
    }

    private User createUser() {
        return User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId("kakao-1")
                .nickname("tester")
                .email("test@test.com")
                .profileUrl("http://img")
                .build();
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
