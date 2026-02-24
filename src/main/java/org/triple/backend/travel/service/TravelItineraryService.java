package org.triple.backend.travel.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.travel.exception.TravelItineraryErrorCode;
import org.triple.backend.travel.dto.request.TravelItinerarySaveRequestDto;
import org.triple.backend.travel.dto.request.TravelItineraryUpdateRequestDto;
import org.triple.backend.travel.dto.response.TravelItinerarySaveResponseDto;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.exception.UserTravelItineraryErrorCode;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

@Service
@RequiredArgsConstructor
public class TravelItineraryService {
    private final UserJpaRepository userJpaRepository;
    private final UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;
    private final TravelItineraryJpaRepository travelItineraryJpaRepository;
    private final GroupJpaRepository groupJpaRepository;
    private final UserGroupJpaRepository userGroupJpaRepository;

    @Transactional
    public TravelItinerarySaveResponseDto saveTravels(final TravelItinerarySaveRequestDto travelsRequestDto, final Long userId) {
        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(TravelItineraryErrorCode.TRAVEL_USER_NOT_FOUND));

        Group group = groupJpaRepository.findByIdForRead(travelsRequestDto.groupId())
                .orElseThrow(() -> new BusinessException(TravelItineraryErrorCode.TRAVEL_GROUP_NOT_FOUND));

        if (!userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(group.getId(), userId, JoinStatus.JOINED)) {
            throw new BusinessException(TravelItineraryErrorCode.SAVE_FORBIDDEN);
        }

        TravelItinerary travelItinerary = TravelItinerary.of(travelsRequestDto, group);
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.save(travelItinerary);

        UserTravelItinerary userTravelItinerary = UserTravelItinerary.of(user, savedTravelItinerary,
                UserRole.LEADER);
        userTravelItineraryJpaRepository.save(userTravelItinerary);

        return TravelItinerarySaveResponseDto.from(savedTravelItinerary.getId());
    }

    @Transactional
    public void updateTravel(TravelItineraryUpdateRequestDto travelItineraryUpdateRequestDto, Long travelItineraryId, Long userId) {
        TravelItinerary travelItinerary = travelItineraryJpaRepository.findByIdAndIsDeletedFalse(travelItineraryId)
                .orElseThrow(() -> new BusinessException(TravelItineraryErrorCode.TRAVEL_NOT_FOUND));

        UserTravelItinerary userTravelItinerary = userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(userId, travelItineraryId)
                .orElseThrow(() -> new BusinessException(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND));

        if(!userTravelItinerary.getUserRole().equals(UserRole.LEADER)) {
            throw new BusinessException(UserTravelItineraryErrorCode.UPDATE_UNAUTHORIZED);
        }

        try {
            travelItinerary.updateTravelItinerary(travelItineraryUpdateRequestDto);
            travelItineraryJpaRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(TravelItineraryErrorCode.CONCURRENT_TRAVEL_ITINERARY_UPDATE);
        }
    }

    @Transactional
    public void deleteTravel(Long travelItineraryId, Long userId) {
        TravelItinerary travelItinerary = travelItineraryJpaRepository.findByIdAndIsDeletedFalse(travelItineraryId)
                .orElseThrow(() -> new BusinessException(TravelItineraryErrorCode.TRAVEL_NOT_FOUND));

        UserTravelItinerary userTravelItinerary = userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(userId, travelItineraryId)
                .orElseThrow(() -> new BusinessException(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND));

        if (!userTravelItinerary.getUserRole().equals(UserRole.LEADER)) {
            throw new BusinessException(UserTravelItineraryErrorCode.DELETE_UNAUTHORIZED);
        }

        try {
            travelItinerary.deleteTravelItinerary();
            travelItineraryJpaRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(TravelItineraryErrorCode.CONCURRENT_TRAVEL_ITINERARY_DELETE);
        }
    }
}
