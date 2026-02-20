package org.triple.backend.group.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.joinApply.JoinStatus;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.exception.JoinApplyErrorCode;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.JoinApplyJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

import static org.triple.backend.group.entity.userGroup.JoinStatus.*;

@Service
@RequiredArgsConstructor
public class JoinApplyService {

    private final UserJpaRepository userJpaRepository;
    private final GroupJpaRepository groupJpaRepository;
    private final JoinApplyJpaRepository joinApplyJpaRepository;
    private final UserGroupJpaRepository userGroupJpaRepository;

    @Transactional
    public void joinApply(final Long groupId, final Long userId) {

        if (userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(groupId, userId, JOINED)) {
            throw new BusinessException(JoinApplyErrorCode.ALREADY_JOINED_GROUP);
        }

        User findUser = userJpaRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        Group findGroup = groupJpaRepository.findById(groupId).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        JoinApply existingApply = joinApplyJpaRepository.findByGroupIdAndUserId(groupId, userId).orElse(null);
        if (existingApply != null) {
            if (existingApply.isCanceled()) {
                existingApply.reapply();
                joinApplyJpaRepository.flush();
                return;
            }
            if (existingApply.getJoinStatus() == JoinStatus.PENDING) {
                throw new BusinessException(JoinApplyErrorCode.ALREADY_APPLY_JOIN_REQUEST);
            }
            throw new BusinessException(JoinApplyErrorCode.REAPPLY_ALLOWED_ONLY_CANCELED);
        }

        try {
            JoinApply joinApply = JoinApply.create(findUser, findGroup);
            joinApplyJpaRepository.save(joinApply);
            joinApplyJpaRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(JoinApplyErrorCode.ALREADY_APPLY_JOIN_REQUEST);
        }
    }

}
