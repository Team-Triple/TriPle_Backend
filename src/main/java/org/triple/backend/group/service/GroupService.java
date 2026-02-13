package org.triple.backend.group.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.repository.GroupJpaRepository;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupJpaRepository groupJpaRepository;

    @Transactional
    public CreateGroupResponseDto create(final CreateGroupRequestDto createGroupRequestDto) {
        Group group = Group.builder()
                .name(createGroupRequestDto.name())
                .description(createGroupRequestDto.description())
                .memberLimit(createGroupRequestDto.memberLimit())
                .groupKind(createGroupRequestDto.groupKind())
                .thumbNailUrl(createGroupRequestDto.thumbNailUrl())
                .build();

        Group savedGroup = groupJpaRepository.save(group);

        return CreateGroupResponseDto.fromEntity(savedGroup);
    }
}
