package org.triple.backend.travel.unit.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.travel.TravelErrorCode;
import org.triple.backend.travel.dto.request.TravelSaveRequestDto;
import org.triple.backend.travel.dto.response.TravelSaveResponseDto;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.travel.service.TravelService;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.time.LocalDateTime;

@ServiceTest
@Import({TravelService.class})
class TravelServiceTest {
    @Autowired
    private TravelService travelService;

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


    @BeforeEach
    void setUp() {
        userTravelItineraryJpaRepository.deleteAll();
        travelItineraryJpaRepository.deleteAll();
        userGroupJpaRepository.deleteAll();
        groupJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("여행 저장 시 유저를 찾을 수 없다면, 예외를 던진다.")
    void 여행_저장_유저_없음_예외() {
        //given
        Long invalidUserId = 1L;

        TravelSaveRequestDto travelSaveRequestDto = new TravelSaveRequestDto(
                "제목",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                1L,
                "설명",
                "test-url",
                5
        );

        //when & then
        Assertions.assertThatThrownBy(() -> travelService.saveTravels(travelSaveRequestDto, invalidUserId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TravelErrorCode.TRAVEL_USER_NOT_FOUND);
    }

    @Test
    @DisplayName("여행 저장 시 그룹을 찾을 수 없다면, 예외를 던진다.")
    void 여행_저장_그룹_없음_예외() {
        //given
        User user = userJpaRepository.save(createUser());
        Long invalidGroupId = 1L;

        TravelSaveRequestDto travelSaveRequestDto = new TravelSaveRequestDto(
                "제목",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                invalidGroupId, //없는 그룹 아이디 넣기
                "설명",
                "test-url",
                5
        );

        //when & then
        Assertions.assertThatThrownBy(() -> travelService.saveTravels(travelSaveRequestDto, user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TravelErrorCode.TRAVEL_GROUP_NOT_FOUND);
    }

    @Test
    @DisplayName("여행 저장 시 해당 그룹에 포함되어있지 않는 사용자라면 예외를 던진다.(권한 없음)")
    void 여행_저장_그룹_사용자_아님_예외() {
        //given
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());

        TravelSaveRequestDto travelSaveRequestDto = new TravelSaveRequestDto(
                "제목",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group.getId(),
                "설명",
                "test-url",
                5
        );

        //when & then
        Assertions.assertThatThrownBy(() -> travelService.saveTravels(travelSaveRequestDto, user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TravelErrorCode.SAVE_FORBIDDEN);
    }

    @Test
    @DisplayName("여행 저장 시 값들이 누락없이 저장된다.")
    void 여행_저장_테스트() {
        //given
        User user = userJpaRepository.save(createUser());
        Group group = groupJpaRepository.save(createGroup());
        userGroupJpaRepository.save(createUserGroup(user, group));

        TravelSaveRequestDto travelSaveRequestDto = new TravelSaveRequestDto(
                "제목",
                LocalDateTime.of(2026, 2, 14, 0, 0),
                LocalDateTime.of(2026, 2, 16, 0, 0),
                group.getId(),
                "설명",
                "test-url",
                5
        );

        //when
        TravelSaveResponseDto answer = travelService.saveTravels(travelSaveRequestDto, user.getId());

        //then
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.findById(answer.itineraryId()).orElse(null);
        Assertions.assertThat(answer).isNotNull();
        Assertions.assertThat(savedTravelItinerary).isNotNull();
        Assertions.assertThat(savedTravelItinerary)
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

    private static Group createGroup() {
        return Group.builder()
                .groupKind(GroupKind.PUBLIC)
                .name("모임")
                .description("설명")
                .thumbNailUrl("http://thumb")
                .memberLimit(10)
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