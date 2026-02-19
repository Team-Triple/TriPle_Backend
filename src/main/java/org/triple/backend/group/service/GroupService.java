package org.triple.backend.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.request.GroupCursorResponseDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.List;

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

    @Transactional(readOnly = true)
    public GroupCursorResponseDto browsePublicGroups(final Long cursor, final int size) {
        int pageSize = Math.min(Math.max(size, 1), 10);
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
}