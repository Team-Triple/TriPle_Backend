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
        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(TravelErrorCode.TRAVEL_USER_NOT_FOUND));

        Group group = groupJpaRepository.findByIdForUpdate(travelsRequestDto.groupId())
                .orElseThrow(() -> new BusinessException(TravelErrorCode.TRAVEL_GROUP_NOT_FOUND));

        if (!userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(group.getId(), userId, JoinStatus.JOINED)) {
            throw new BusinessException(TravelErrorCode.SAVE_FORBIDDEN);
        }

        TravelItinerary travelItinerary = TravelItinerary.of(travelsRequestDto, group);
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.save(travelItinerary);

        UserTravelItinerary userTravelItinerary = UserTravelItinerary.of(user, savedTravelItinerary,
                UserRole.LEADER);
        userTravelItineraryJpaRepository.save(userTravelItinerary);

        return TravelSaveResponseDto.from(savedTravelItinerary.getId());
    }
}
