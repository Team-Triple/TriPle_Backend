package org.triple.backend.travel.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.travel.TravelErrorCode;
import org.triple.backend.travel.dto.request.TravelSaveRequestDto;
import org.triple.backend.travel.dto.response.TravelSaveResponseDto;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

@Service
@RequiredArgsConstructor
public class TravelService {
    private final UserJpaRepository userJpaRepository;
    private final UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;
    private final TravelItineraryJpaRepository travelItineraryJpaRepository;
    private final GroupJpaRepository groupJpaRepository;
    private final UserGroupJpaRepository userGroupJpaRepository;

    @Transactional
    public TravelSaveResponseDto saveTravels(final TravelSaveRequestDto travelsRequestDto, final Long userId) {
        User user = userJpaRepository.findById(userId) //유저 찾기
                .orElseThrow(() -> new BusinessException(TravelErrorCode.TRAVEL_USER_NOT_FOUND));

        Group group = groupJpaRepository.findById(travelsRequestDto.groupId()) //그룹 찾기
                .orElseThrow(() -> new BusinessException(TravelErrorCode.TRAVEL_GROUP_NOT_FOUND));

        if (!userGroupJpaRepository.existsByUserAndGroupAndJoinStatus(user, group, JoinStatus.JOINED)) { //그룹에 포함되지 않은 사용자면 예외
            throw new BusinessException(TravelErrorCode.SAVE_FORBIDDEN);
        }

        TravelItinerary travelItinerary = TravelItinerary.of(travelsRequestDto, group); //여행 일정 생성
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.save(travelItinerary); //저장

        UserTravelItinerary userTravelItinerary = UserTravelItinerary.of(user, savedTravelItinerary, //여행-유저 매핑 생성
                UserRole.LEADER);
        userTravelItineraryJpaRepository.save(userTravelItinerary); //저장

        return TravelSaveResponseDto.from(savedTravelItinerary.getId()); //dto 반환
    }
}
