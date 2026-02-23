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
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.dto.response.GroupDetailResponseDto;
import org.triple.backend.group.dto.response.GroupUpdateResponseDto;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.JoinApplyJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 10;

    private final GroupJpaRepository groupJpaRepository;
    private final UserGroupJpaRepository userGroupJpaRepository;
    private final JoinApplyJpaRepository joinApplyJpaRepository;
    private final UserJpaRepository userJpaRepository;

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
        int pageSize = Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<Group> rows = (cursor == null) ? groupJpaRepository.findPublicFirstPage(GroupKind.PUBLIC, pageable)
                : groupJpaRepository.findPublicNextPage(GroupKind.PUBLIC, cursor, pageable);

        boolean hasNext = rows.size() > pageSize;

        if(hasNext) {
            rows = rows.subList(0, pageSize);
        }

        Long nextCursor = hasNext ? rows.get(rows.size() - 1).getId() : null;

        return GroupCursorResponseDto.from(rows, nextCursor, hasNext);
    }

    @Transactional
    public void delete(final Long groupId, final Long userId) {

       groupJpaRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        if(!userGroupJpaRepository.existsByGroupIdAndUserIdAndRole(groupId, userId, Role.OWNER)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_OWNER);
        }

        joinApplyJpaRepository.bulkDeleteByGroupId(groupId);
        userGroupJpaRepository.bulkDeleteByGroupId(groupId);

        groupJpaRepository.deleteById(groupId);
    }

    @Transactional
    public GroupUpdateResponseDto update(final GroupUpdateRequestDto dto, final Long groupId, final Long userId) {

        try {
            Group group = groupJpaRepository.findById(groupId).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

            if(!userGroupJpaRepository.existsByGroupIdAndUserIdAndRole(groupId, userId, Role.OWNER)) {
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

        Group group = groupJpaRepository.findById(groupId).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));
        userJpaRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if(group.getGroupKind().equals(GroupKind.PRIVATE)) {
            if(!userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(groupId, userId, JoinStatus.JOINED)) {
                throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
            }
        }

        List<UserGroup> userGroups = userGroupJpaRepository.findAllByGroupIdAndJoinStatus(groupId, JoinStatus.JOINED);

        return GroupDetailResponseDto.from(userGroups, group);
    }
}
