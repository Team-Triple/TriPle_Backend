package org.triple.backend.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.request.GroupUpdateRequestDto;
import org.triple.backend.group.dto.response.*;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.exception.GroupErrorCode;
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
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;
import org.triple.backend.user.service.UserFinder;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.*;
import static org.triple.backend.group.dto.response.GroupDetailResponseDto.*;

@Service
@RequiredArgsConstructor
public class GroupService {

    private static final int KEYWORD_MAX_LENGTH = 20;
    private static final int DETAIL_RECENT_SIZE = 4;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 10;

    private final GroupJpaRepository groupJpaRepository;
    private final UserGroupJpaRepository userGroupJpaRepository;
    private final JoinApplyJpaRepository joinApplyJpaRepository;
    private final TravelItineraryJpaRepository travelItineraryJpaRepository;
    private final TravelReviewJpaRepository travelReviewJpaRepository;
    private final TravelReviewImageJpaRepository travelReviewImageJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final UserFinder userFinder;

    @Transactional
    public CreateGroupResponseDto create(final CreateGroupRequestDto dto, final Long userId) {

        User user = userJpaRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        Group group = Group.create(dto.groupKind(), dto.name(), dto.description(), dto.thumbNailUrl(), dto.memberLimit());

        group.addMember(user, Role.OWNER);
        Group savedGroup = groupJpaRepository.save(group);

        return new CreateGroupResponseDto(savedGroup.getId());
    }

    @Transactional(readOnly = true)
    public GroupCursorResponseDto browsePublicGroups(final Long cursor, final int size) {
        int pageSize = normalizePageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<Group> rows = (cursor == null) ? groupJpaRepository.findPublicFirstPage(GroupKind.PUBLIC, pageable)
                : groupJpaRepository.findPublicNextPage(GroupKind.PUBLIC, cursor, pageable);

        return toCursorResponse(rows, pageSize);
    }

    @Transactional
    public void delete(final Long groupId, final Long userId) {

        Group group = groupJpaRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        if(!userGroupJpaRepository.existsByGroupIdAndUserIdAndRoleAndJoinStatus(groupId, userId, Role.OWNER, JoinStatus.JOINED)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_OWNER);
        }

        if (group.getCurrentMemberCount() != 1) {
            throw new BusinessException(GroupErrorCode.CANNOT_DELETE_GROUP_WITH_MEMBERS);
        }

        group.deleteGroup();
        groupJpaRepository.flush();
        joinApplyJpaRepository.bulkDeleteByGroupId(groupId);
        userGroupJpaRepository.bulkDeleteByGroupId(groupId);
    }

    @Transactional
    public GroupUpdateResponseDto update(final GroupUpdateRequestDto dto, final Long groupId, final Long userId) {

        try {
            Group group = groupJpaRepository.findByIdAndIsDeletedFalse(groupId).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

            if(!userGroupJpaRepository.existsByGroupIdAndUserIdAndRoleAndJoinStatus(groupId, userId, Role.OWNER, JoinStatus.JOINED)) {
                throw new BusinessException(GroupErrorCode.NOT_GROUP_OWNER);
            }

            group.update(dto.groupKind(), dto.name(), dto.description(), dto.thumbNailUrl(), dto.memberLimit());
            groupJpaRepository.flush();

            return new GroupUpdateResponseDto(
                    group.getId(),
                    group.getGroupKind(),
                    group.getName(),
                    group.getDescription(),
                    group.getThumbNailUrl(),
                    group.getMemberLimit(),
                    group.getCurrentMemberCount()
            );
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(GroupErrorCode.CONCURRENT_GROUP_UPDATE);
        }
    }

    @Transactional(readOnly = true)
    public GroupDetailResponseDto detail(final Long groupId, final Long userId) {

        Group group = groupJpaRepository.findByIdAndIsDeletedFalse(groupId).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        UserGroup myUserGroup = userGroupJpaRepository.findByGroupIdAndUserIdAndJoinStatus(groupId, userId, JoinStatus.JOINED)
                .orElse(null);

        if(group.getGroupKind().equals(GroupKind.PRIVATE) && myUserGroup == null) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        List<UserGroup> userGroups = userGroupJpaRepository.findAllByGroupIdAndJoinStatus(groupId, JoinStatus.JOINED);

        Pageable detailRecentPage = PageRequest.of(0, DETAIL_RECENT_SIZE);
        List<RecentTravelDto> recentTravels = travelItineraryJpaRepository.findRecentByGroupId(groupId, detailRecentPage)
                .stream()
                .map(this::toRecentTravelDto)
                .toList();

        List<TravelReview> recentReviewEntities = travelReviewJpaRepository.findRecentByGroupId(groupId, detailRecentPage);
        Map<Long, String> reviewImageUrlByReviewId = findFirstReviewImageUrlByReviewId(recentReviewEntities);

        List<RecentReviewDto> recentReviews = recentReviewEntities
                .stream()
                .map(review -> toRecentReviewDto(review, reviewImageUrlByReviewId.get(review.getId())))
                .toList();

        List<RecentPhotoDto> recentPhotos = travelReviewImageJpaRepository.findRecentByGroupId(groupId, detailRecentPage)
                .stream()
                .map(this::toRecentPhotoDto)
                .toList();

        Role myRole = myUserGroup == null ? Role.GUEST : myUserGroup.getRole();

        return from(userGroups, group, myRole, recentPhotos, recentTravels, recentReviews);
    }

    private RecentPhotoDto toRecentPhotoDto(final TravelReviewImage reviewImage) {
        return new RecentPhotoDto(
                reviewImage.getId(),
                reviewImage.getReviewImageUrl()
        );
    }

    private RecentTravelDto toRecentTravelDto(final TravelItinerary itinerary) {
        return new RecentTravelDto(
                itinerary.getId(),
                itinerary.getTitle(),
                itinerary.getThumbnailUrl(),
                itinerary.getDescription(),
                itinerary.getMemberCount(),
                itinerary.getMemberLimit(),
                itinerary.getStartAt(),
                itinerary.getEndAt()
        );
    }

    private RecentReviewDto toRecentReviewDto(final TravelReview review, final String imageUrl) {
        return new RecentReviewDto(
                review.getId(),
                review.getTravelItinerary().getTitle(),
                review.getContent(),
                review.getUser().getNickname(),
                imageUrl,
                review.getView(),
                review.getCreatedAt()
        );
    }

    private Map<Long, String> findFirstReviewImageUrlByReviewId(final List<TravelReview> reviews) {
        if (reviews.isEmpty()) {
            return Map.of();
        }

        List<Long> reviewIds = reviews.stream()
                .map(TravelReview::getId)
                .toList();

        return travelReviewImageJpaRepository.findAllByReviewIds(reviewIds).stream()
                .collect(toMap(
                        image -> image.getTravelReview().getId(),
                        TravelReviewImage::getReviewImageUrl,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    @Transactional
    public void ownerTransfer(final Long groupId, final String targetUserId, final Long ownerId) {
        Long targetInternalUserId = resolveTargetUserIdOrThrow(targetUserId);

        if(ownerId.equals(targetInternalUserId)) {
            throw new BusinessException(GroupErrorCode.CANNOT_OWNER_DEMOTE_SELF);
        }

        groupJpaRepository.findByIdForUpdate(groupId).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        UserGroup ownerUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(groupId, ownerId).orElseThrow(() -> new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER));

        if(ownerUserGroup.getRole() != Role.OWNER) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_OWNER);
        }

        if(ownerUserGroup.getJoinStatus() != JoinStatus.JOINED) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        UserGroup targetUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(groupId, targetInternalUserId).orElseThrow(() -> new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER));

        if(targetUserGroup.getJoinStatus() != JoinStatus.JOINED) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        targetUserGroup.transferRole(Role.OWNER);
        ownerUserGroup.transferRole(Role.MEMBER);
    }

    @Transactional
    public void kick(final Long groupId, final Long ownerId, final String targetUserId) {
        Long targetInternalUserId = resolveTargetUserIdOrThrow(targetUserId);

        if(ownerId.equals(targetInternalUserId)) {
            throw new BusinessException(GroupErrorCode.CANNOT_KICK_SELF);
        }

        UserGroup userGroup = userGroupJpaRepository.findByGroupIdAndUserId(groupId, ownerId).orElseThrow(() -> new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER));
        if(userGroup.getRole() != Role.OWNER) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_OWNER);
        }
        if(userGroup.getJoinStatus() != JoinStatus.JOINED) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        UserGroup targetUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(groupId, targetInternalUserId).orElseThrow(() -> new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER));
        if(targetUserGroup.getJoinStatus() != JoinStatus.JOINED) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }
        if(targetUserGroup.getRole() == Role.OWNER) {
            throw new BusinessException(GroupErrorCode.CANNOT_KICK_OWNER);
        }

        joinApplyJpaRepository.deleteByGroupIdAndUserId(groupId, targetInternalUserId);

        Group group = userGroup.getGroup();
        targetUserGroup.leave();
        group.decreaseCurrentMemberCount();

        try {
            groupJpaRepository.flush();
        } catch(OptimisticLockingFailureException e) {
            throw new BusinessException(GroupErrorCode.CONCURRENT_GROUP_UPDATE);
        }
    }

    @Transactional
    public void leave(final Long groupId, final Long userId) {
        if (!userJpaRepository.existsById(userId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        UserGroup userGroup = userGroupJpaRepository.findByGroupIdAndUserId(groupId, userId).orElseThrow(() -> new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER));

        if(userGroup.getRole() == Role.OWNER) {
            throw new BusinessException(GroupErrorCode.GROUP_OWNER_NOT_LEAVE);
        }

        if(userGroup.getJoinStatus() != JoinStatus.JOINED) {
            throw new BusinessException(GroupErrorCode.ALREADY_LEAVE_GROUP);
        }

        joinApplyJpaRepository.deleteByGroupIdAndUserId(groupId, userId);

        Group group = userGroup.getGroup();
        userGroup.leave();
        group.decreaseCurrentMemberCount();

        try {
            groupJpaRepository.flush();
        } catch(OptimisticLockingFailureException e) {
            throw new BusinessException(GroupErrorCode.CONCURRENT_GROUP_UPDATE);
        }
    }

    @Transactional(readOnly = true)
    public GroupCursorResponseDto myGroups(final Long cursor, final int size, final Long userId) {
        if(!userJpaRepository.existsById(userId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        int pageSize = normalizePageSize(size);
        Pageable pageRequest = PageRequest.of(0, pageSize + 1);

        List<Group> rows = cursor == null ? userGroupJpaRepository.findMyGroupsFirstPage(userId, JoinStatus.JOINED, pageRequest)
                : userGroupJpaRepository.findMyGroupsNextPage(userId, JoinStatus.JOINED, cursor, pageRequest);

        return toCursorResponse(rows, pageSize);
    }

    private int normalizePageSize(int size) {
        return Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
    }

    private GroupCursorResponseDto toCursorResponse(List<Group> rows, int pageSize) {
        boolean hasNext = rows.size() > pageSize;
        if(hasNext) {
            rows = rows.subList(0, pageSize);
        }

        Long nextCursor = hasNext ? rows.get(rows.size() - 1).getId() : null;
        return GroupCursorResponseDto.from(rows, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public GroupCursorResponseDto search(final String keyword, final Long cursor, final int size) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        if (normalizedKeyword.isBlank()) {
            return browsePublicGroups(cursor, size);
        }

        if(normalizedKeyword.length() > KEYWORD_MAX_LENGTH) {
            throw new BusinessException(GroupErrorCode.INVALID_SEARCH_KEYWORD_LENGTH);
        }

        int pageSize = Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<Group> rows = cursor == null
                ? findFirstPageByKeyword(normalizedKeyword, pageable)
                : findNextPageByKeyword(normalizedKeyword, cursor, pageable);

        return toCursorResponse(rows, pageSize);
    }

    @Transactional(readOnly = true)
    public GroupMenuResponseDto menu(final Long userId, final Long groupId) {
        Group group = groupJpaRepository.findByIdAndIsDeletedFalse(groupId).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));
        UserGroup userGroup = null;

        if (userId != null) {
            userGroup = userGroupJpaRepository.findByGroupIdAndUserIdAndJoinStatus(groupId, userId, JoinStatus.JOINED).orElse(null);
        }

        if (group.getGroupKind() == GroupKind.PRIVATE && userGroup == null) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        Role role = userGroup == null ? Role.GUEST : userGroup.getRole();

        return new GroupMenuResponseDto(group.getName(), group.getDescription(), group.getCurrentMemberCount(), group.getMemberLimit(), group.getThumbNailUrl(), role);
    }

    @Transactional(readOnly = true)
    public GroupUsersResponseDto groupUsers(final Long groupId) {
        Group group = groupJpaRepository.findByIdAndIsDeletedFalse(groupId)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        if(group.getGroupKind() == GroupKind.PRIVATE) {
            throw new BusinessException(GroupErrorCode.CANNOT_GET_PRIVATE_GROUP_MEMBERS);
        }

        List<UserGroup> userGroups = userGroupJpaRepository.findAllByGroupIdAndJoinStatus(groupId, JoinStatus.JOINED);
        return GroupUsersResponseDto.from(userGroups);
    }

    private Long resolveTargetUserIdOrThrow(final String targetUserId) {
        return userFinder.findIdByPublicUuidOrThrow(targetUserId, GroupErrorCode.NOT_GROUP_MEMBER);
    }

    private List<Group> findFirstPageByKeyword(String keyword, Pageable pageable) {
        String booleanQuery = toBooleanModeQuery(keyword);
        if (booleanQuery.isBlank()) {
            return List.of();
        }

        return groupJpaRepository.findFirstPageByKeywordFullText(booleanQuery, GroupKind.PUBLIC.name(), pageable);
    }

    private List<Group> findNextPageByKeyword(String keyword, Long cursor, Pageable pageable) {
        String booleanQuery = toBooleanModeQuery(keyword);
        if (booleanQuery.isBlank()) {
            return List.of();
        }

        return groupJpaRepository.findNextPageByKeywordFullText(booleanQuery, cursor, GroupKind.PUBLIC.name(), pageable);
    }

    private String toBooleanModeQuery(String keyword) {
        return Arrays.stream(keyword.trim().split("[^\\p{L}\\p{N}]+"))
                .filter(token -> !token.isBlank())
                .map(token -> "+" + token + "*")
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }
}
