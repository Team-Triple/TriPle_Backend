package org.triple.backend.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupJpaRepository groupJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Transactional
    public CreateGroupResponseDto create(final CreateGroupRequestDto createGroupRequestDto, final Long userId) {

        User user = userJpaRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        Group group = Group.builder()
                .name(createGroupRequestDto.name())
                .description(createGroupRequestDto.description())
                .memberLimit(createGroupRequestDto.memberLimit())
                .groupKind(createGroupRequestDto.groupKind())
                .thumbNailUrl(createGroupRequestDto.thumbNailUrl())
                .build();

        group.addMember(user, Role.OWNER);
        Group savedGroup = groupJpaRepository.save(group);

        return new CreateGroupResponseDto(savedGroup.getId());
    }
}