package org.triple.backend.travel.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.travel.exception.TravelItineraryErrorCode;
import org.triple.backend.travel.dto.request.TravelItinerarySaveRequestDto;
import org.triple.backend.travel.dto.request.TravelItineraryUpdateRequestDto;
import org.triple.backend.travel.dto.response.TravelItineraryCursorResponseDto;
import org.triple.backend.travel.dto.response.TravelItinerarySaveResponseDto;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.exception.UserTravelItineraryErrorCode;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TravelItineraryService {
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 10;

    private final UserJpaRepository userJpaRepository;
    private final UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;
    private final TravelItineraryJpaRepository travelItineraryJpaRepository;
    private final GroupJpaRepository groupJpaRepository;
    private final UserGroupJpaRepository userGroupJpaRepository;
    private final SessionManager sessionManager;

    @Transactional
    public TravelItinerarySaveResponseDto saveTravels(final TravelItinerarySaveRequestDto travelsRequestDto, final Long userId) {
        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        Group group = groupJpaRepository.findByIdForRead(travelsRequestDto.groupId())
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        if (!userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(group.getId(), userId, JoinStatus.JOINED)) {
            throw new BusinessException(TravelItineraryErrorCode.SAVE_FORBIDDEN);
        }

        TravelItinerary travelItinerary = TravelItinerary.of(travelsRequestDto, group);
        TravelItinerary savedTravelItinerary = travelItineraryJpaRepository.save(travelItinerary);

        UserTravelItinerary userTravelItinerary = UserTravelItinerary.of(user, savedTravelItinerary,
                UserRole.LEADER);
        userTravelItineraryJpaRepository.save(userTravelItinerary);
        addTravelMembers(
                travelsRequestDto.memberUuids(),
                savedTravelItinerary,
                group.getId(),
                userId
        );

        return TravelItinerarySaveResponseDto.from(savedTravelItinerary.getId());
    }

    private void addTravelMembers(
            final List<UUID> memberUuids,
            final TravelItinerary travelItinerary,
            final Long groupId,
            final Long leaderUserId
    ) {
        if (memberUuids == null || memberUuids.isEmpty()) {
            return;
        }

        Set<Long> addedUserIds = new HashSet<>();
        addedUserIds.add(leaderUserId);

        for (UUID memberUuid : memberUuids) {
            Long memberUserId = sessionManager.resolveUserId(memberUuid);
            if (memberUserId == null) {
                throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
            }

            if (!addedUserIds.add(memberUserId)) {
                continue;
            }

            User member = userJpaRepository.findById(memberUserId)
                    .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

            if (!userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(groupId, memberUserId, JoinStatus.JOINED)) {
                throw new BusinessException(TravelItineraryErrorCode.SAVE_FORBIDDEN);
            }

            userTravelItineraryJpaRepository.save(UserTravelItinerary.of(member, travelItinerary, UserRole.MEMBER));
            travelItinerary.increaseMemberCount();
        }
    }

    @Transactional
    public void joinTravel(final Long travelItineraryId, final Long userId) {
        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        TravelItinerary travelItinerary = travelItineraryJpaRepository.findByIdAndIsDeletedFalseForUpdate(travelItineraryId)
                .orElseThrow(() -> new BusinessException(TravelItineraryErrorCode.TRAVEL_NOT_FOUND));

        Long groupId = travelItinerary.getGroup().getId();
        if (!userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(groupId, userId, JoinStatus.JOINED)) {
            throw new BusinessException(TravelItineraryErrorCode.JOIN_FORBIDDEN);
        }

        if (userTravelItineraryJpaRepository.existsByUserIdAndTravelItineraryId(userId, travelItineraryId)) {
            throw new BusinessException(UserTravelItineraryErrorCode.ALREADY_JOINED_TRAVEL);
        }

        try {
            travelItinerary.increaseMemberCount();
            userTravelItineraryJpaRepository.save(UserTravelItinerary.of(user, travelItinerary, UserRole.MEMBER));
            travelItineraryJpaRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(TravelItineraryErrorCode.CONCURRENT_TRAVEL_ITINERARY_JOIN);
        }
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

    @Transactional(readOnly = true)
    public TravelItineraryCursorResponseDto browseTravels(
            final Long groupId,
            final Long cursor,
            final int size,
            final Long userId
    ) {
        long count = travelItineraryJpaRepository.countByGroupIdAndIsDeletedFalse(groupId);

        if (userId == null) {
            return TravelItineraryCursorResponseDto.countOnly(count);
        }

        if (!userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(groupId, userId, JoinStatus.JOINED)) {
            return TravelItineraryCursorResponseDto.countOnly(count);
        }

        int pageSize = normalizePageSize(size);
        Pageable pageRequest = PageRequest.of(0, pageSize + 1);

        List<TravelItinerary> rows = cursor == null
                ? travelItineraryJpaRepository.findGroupTravelsFirstPage(groupId, pageRequest)
                : travelItineraryJpaRepository.findGroupTravelsNextPage(groupId, cursor, pageRequest);

        boolean hasNext = rows.size() > pageSize;
        if (hasNext) {
            rows = rows.subList(0, pageSize);
        }

        Long nextCursor = hasNext ? rows.get(rows.size() - 1).getId() : null;
        return TravelItineraryCursorResponseDto.of(rows, nextCursor, hasNext, count);
    }

    private int normalizePageSize(int size) {
        return Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
    }

    @Transactional
    public void leaveTravel(final Long travelItineraryId, final Long userId) {
        UserTravelItinerary userTravelItinerary = userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(userId, travelItineraryId)
                .orElseThrow(() -> new BusinessException(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND));

        TravelItinerary travelItinerary = userTravelItinerary.getTravelItinerary();
        if(travelItinerary == null) {
            throw new BusinessException(TravelItineraryErrorCode.TRAVEL_NOT_FOUND);
        }

        if (userTravelItinerary.getUserRole().equals(UserRole.LEADER)) {
            throw new BusinessException(UserTravelItineraryErrorCode.LEAVE_UNAUTHORIZED);
        }

        try {
            travelItinerary.decreaseMemberCount();
            userTravelItineraryJpaRepository.delete(userTravelItinerary);
            userTravelItineraryJpaRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(UserTravelItineraryErrorCode.CONCURRENT_TRAVEL_ITINERARY_LEAVE);
        }
    }
}
