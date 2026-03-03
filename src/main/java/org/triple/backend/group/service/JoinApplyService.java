package org.triple.backend.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.dto.response.JoinApplyUserResponseDto;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.joinApply.JoinApplyStatus;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.exception.JoinApplyErrorCode;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.JoinApplyJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.List;

import static org.triple.backend.group.dto.response.JoinApplyUserResponseDto.*;
import static org.triple.backend.group.entity.userGroup.JoinStatus.JOINED;
import static org.triple.backend.group.exception.JoinApplyErrorCode.*;

@Service
@RequiredArgsConstructor
public class JoinApplyService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 10;

    private final UserJpaRepository userJpaRepository;
    private final GroupJpaRepository groupJpaRepository;
    private final JoinApplyJpaRepository joinApplyJpaRepository;
    private final UserGroupJpaRepository userGroupJpaRepository;

    @Transactional
    public void joinApply(final Long groupId, final Long userId) {

        if (userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(groupId, userId, JOINED)) {
            throw new BusinessException(ALREADY_JOINED_GROUP);
        }

        User findUser = userJpaRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        Group findGroup = groupJpaRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        JoinApply existingApply = joinApplyJpaRepository.findByGroupIdAndUserId(groupId, userId).orElse(null);
        if (existingApply != null) {
            switch (existingApply.getJoinApplyStatus()) {
                case CANCELED:
                    existingApply.reapply();
                    return;
                case PENDING:
                    throw new BusinessException(ALREADY_APPLY_JOIN_REQUEST);
                default:
                    throw new BusinessException(REAPPLY_ALLOWED_ONLY_CANCELED);
            }
        }

        try {
            JoinApply joinApply = JoinApply.create(findUser, findGroup);
            joinApplyJpaRepository.save(joinApply);
            joinApplyJpaRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ALREADY_APPLY_JOIN_REQUEST);
        }
    }

    @Transactional
    public void approve(final Long groupId, final Long ownerUserId, final Long joinApplyId) {

        if(!userJpaRepository.existsById(ownerUserId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        Group findGroup = groupJpaRepository.findByIdForUpdate(groupId).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        if(!userGroupJpaRepository.existsByGroupIdAndUserIdAndRoleAndJoinStatus(groupId, ownerUserId, Role.OWNER, JoinStatus.JOINED)) {
            throw new BusinessException(NO_SIGNUP_APPROVAL_PERMISSION);
        }

        JoinApply joinApply = joinApplyJpaRepository.findByIdAndGroupIdAndJoinApplyStatus(joinApplyId, groupId , JoinApplyStatus.PENDING).orElseThrow(() -> new BusinessException(JoinApplyErrorCode.JOIN_APPLY_NOT_FOUND));
        User applicantUser = joinApply.getUser();

        UserGroup existingUserGroup = userGroupJpaRepository.findByGroupIdAndUserId(groupId, applicantUser.getId()).orElse(null);
        if (existingUserGroup != null) {
            if (existingUserGroup.getJoinStatus() == JoinStatus.JOINED) {
                throw new BusinessException(ALREADY_JOINED_GROUP);
            }
            existingUserGroup.rejoin(Role.MEMBER);
        } else {
            try {
                UserGroup userGroup = UserGroup.create(applicantUser, findGroup, Role.MEMBER);
                userGroupJpaRepository.saveAndFlush(userGroup);
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(ALREADY_JOINED_GROUP);
            }
        }

        findGroup.addCurrentMemberCount();
        joinApply.approve();
    }

    @Transactional(readOnly = true)
    public JoinApplyUserResponseDto joinApplyUser(final Long groupId, final Long userId, final JoinApplyStatus status, final Long cursor, final int size) {
        if(!groupJpaRepository.existsByIdAndIsDeletedFalse(groupId)) {
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        }

        if(!userGroupJpaRepository.existsByGroupIdAndUserIdAndRoleAndJoinStatus(groupId, userId, Role.OWNER, JoinStatus.JOINED)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_OWNER);
        }

        int pageSize = normalizePageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<JoinApply> joinApplies;
        if (status == null) {
            joinApplies = cursor == null
                    ? joinApplyJpaRepository.findFirstPageByGroupId(groupId, pageable)
                    : joinApplyJpaRepository.findNextPageByGroupId(groupId, cursor, pageable);
        } else {
            joinApplies = cursor == null
                    ? joinApplyJpaRepository.findFirstPageByGroupIdAndStatus(groupId, status, pageable)
                    : joinApplyJpaRepository.findNextPageByGroupIdAndStatus(groupId, status, cursor, pageable);
        }

        boolean hasNext = joinApplies.size() > pageSize;
        if (hasNext) {
            joinApplies = joinApplies.subList(0, pageSize);
        }

        List<UserDto> users = joinApplies
                .stream()
                .map(ja -> new UserDto(
                        ja.getId(),
                        ja.getUser().getNickname(),
                        ja.getUser().getDescription(),
                        ja.getUser().getProfileUrl(),
                        ja.getJoinApplyStatus()
                ))
                .toList();

        Long nextCursor = hasNext ? joinApplies.get(joinApplies.size() - 1).getId() : null;
        return new JoinApplyUserResponseDto(users, nextCursor, hasNext);
    }

    private int normalizePageSize(final int size) {
        return Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
    }
}
