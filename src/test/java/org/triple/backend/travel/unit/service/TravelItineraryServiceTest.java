package org.triple.backend.travel.unit.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    @DisplayName("여행 탈퇴 요청 시 참가 정보가 삭제된다.")
    void 여행_탈퇴_요청_시_참가_정보가_삭제된다() {
        Group group = groupJpaRepository.save(createGroup());
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.save(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                "test-thumbnailUrl",
                20,
                2,
                false));
        User user = userJpaRepository.save(createUser());
        userTravelItineraryJpaRepository.save(new UserTravelItinerary(user, savedTravelItinerary, UserRole.MEMBER));

        travelItineraryService.leaveTravel(savedTravelItinerary.getId(), user.getId());

        Assertions.assertThat(userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(user.getId(), savedTravelItinerary.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("여행 리더는 탈퇴 요청 시 예외를 던진다.")
    void 여행_리더는_탈퇴_요청_시_예외를_던진다() {
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

        Assertions.assertThatThrownBy(() -> travelItineraryService.leaveTravel(savedTravelItinerary.getId(), user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserTravelItineraryErrorCode.LEAVE_UNAUTHORIZED);
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

    @Test
    @DisplayName("여행 참가 요청 시 참가 정보가 저장되고 인원이 증가한다.")
    void 여행_참가_요청_시_참가_정보가_저장되고_인원이_증가한다() {
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
                "test-thumbnailUrl",
                3,
                1,
                false
        ));
        userTravelItineraryJpaRepository.save(new UserTravelItinerary(leader, travelItinerary, UserRole.LEADER));

        travelItineraryService.joinTravel(travelItinerary.getId(), joiner.getId());

        TravelItinerary updated = travelItineraryJpaRepository.findById(travelItinerary.getId()).orElseThrow();
        UserTravelItinerary joined = userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(joiner.getId(), travelItinerary.getId()).orElseThrow();
        Assertions.assertThat(updated.getMemberCount()).isEqualTo(2);
        Assertions.assertThat(joined.getUserRole()).isEqualTo(UserRole.MEMBER);
    }

    @Test
    @DisplayName("이미 참가한 유저가 다시 참가하면 예외를 던진다.")
    void 이미_참가한_유저가_다시_참가하면_예외를_던진다() {
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
                "test-thumbnailUrl",
                3,
                2,
                false
        ));
        userTravelItineraryJpaRepository.save(new UserTravelItinerary(leader, travelItinerary, UserRole.LEADER));
        userTravelItineraryJpaRepository.save(new UserTravelItinerary(joiner, travelItinerary, UserRole.MEMBER));

        Assertions.assertThatThrownBy(() -> travelItineraryService.joinTravel(travelItinerary.getId(), joiner.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserTravelItineraryErrorCode.ALREADY_JOINED_TRAVEL);
    }

    @Test
    @DisplayName("그룹 멤버가 아니면 여행 참가 시 예외를 던진다.")
    void 그룹_멤버가_아니면_여행_참가_시_예외를_던진다() {
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
                "test-thumbnailUrl",
                3,
                1,
                false
        ));
        userTravelItineraryJpaRepository.save(new UserTravelItinerary(leader, travelItinerary, UserRole.LEADER));

        Assertions.assertThatThrownBy(() -> travelItineraryService.joinTravel(travelItinerary.getId(), outsider.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TravelItineraryErrorCode.JOIN_FORBIDDEN);
    }

    @Test
    @DisplayName("여행 정원이 가득 차면 참가 시 예외를 던진다.")
    void 여행_정원이_가득_차면_참가_시_예외를_던진다() {
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
                "test-thumbnailUrl",
                1,
                1,
                false
        ));
        userTravelItineraryJpaRepository.save(new UserTravelItinerary(leader, travelItinerary, UserRole.LEADER));

        Assertions.assertThatThrownBy(() -> travelItineraryService.joinTravel(travelItinerary.getId(), joiner.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TravelItineraryErrorCode.TRAVEL_MEMBER_LIMIT_EXCEEDED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("여행 참가 요청이 동시에 들어오면 정원을 초과하지 않고 하나만 성공한다.")
    void 여행_참가_요청이_동시에_들어오면_정원을_초과하지_않고_하나만_성공한다() throws InterruptedException {
        User leader = userJpaRepository.save(createUser());
        User firstJoiner = userJpaRepository.save(createUserWithProviderId("kakao-6"));
        User secondJoiner = userJpaRepository.save(createUserWithProviderId("kakao-7"));
        Group group = groupJpaRepository.save(createGroup());
        userGroupJpaRepository.save(createUserGroup(leader, group));
        userGroupJpaRepository.save(createUserGroup(firstJoiner, group));
        userGroupJpaRepository.save(createUserGroup(secondJoiner, group));

        TravelItinerary travelItinerary = travelItineraryJpaRepository.saveAndFlush(new TravelItinerary(
                "title",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group,
                "description",
                "test-thumbnailUrl",
                2,
                1,
                false
        ));
        userTravelItineraryJpaRepository.saveAndFlush(new UserTravelItinerary(leader, travelItinerary, UserRole.LEADER));

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable joinFirst = () -> {
            ready.countDown();
            try {
                start.await();
                travelItineraryService.joinTravel(travelItinerary.getId(), firstJoiner.getId());
                successCount.incrementAndGet();
            } catch (Throwable throwable) {
                failures.add(throwable);
            } finally {
                done.countDown();
            }
        };

        Runnable joinSecond = () -> {
            ready.countDown();
            try {
                start.await();
                travelItineraryService.joinTravel(travelItinerary.getId(), secondJoiner.getId());
                successCount.incrementAndGet();
            } catch (Throwable throwable) {
                failures.add(throwable);
            } finally {
                done.countDown();
            }
        };

        executorService.submit(joinFirst);
        executorService.submit(joinSecond);

        Assertions.assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        Assertions.assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

        long limitedOrConcurrentCount = failures.stream()
                .filter(BusinessException.class::isInstance)
                .map(BusinessException.class::cast)
                .map(BusinessException::getErrorCode)
                .filter(errorCode -> errorCode == TravelItineraryErrorCode.TRAVEL_MEMBER_LIMIT_EXCEEDED
                        || errorCode == TravelItineraryErrorCode.CONCURRENT_TRAVEL_ITINERARY_JOIN)
                .count();

        TravelItinerary updated = travelItineraryJpaRepository.findById(travelItinerary.getId()).orElseThrow();
        long joinedCount = userTravelItineraryJpaRepository.findAll().stream()
                .filter(mapping -> mapping.getTravelItinerary().getId().equals(travelItinerary.getId()))
                .count();

        Assertions.assertThat(successCount.get()).isEqualTo(1);
        Assertions.assertThat(failures).hasSize(1);
        Assertions.assertThat(limitedOrConcurrentCount).isEqualTo(1);
        Assertions.assertThat(updated.getMemberCount()).isEqualTo(2);
        Assertions.assertThat(joinedCount).isEqualTo(2);

        executorService.shutdownNow();
    }

    private static Group createGroup() {
        return Group.create(GroupKind.PUBLIC, "모임", "설명", "http://thumb", 10);
    }

    private User createUserWithProviderId(String providerId) {
        return User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId(providerId)
                .nickname("tester-" + providerId)
                .email(providerId + "@test.com")
                .profileUrl("http://img")
                .build();
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
