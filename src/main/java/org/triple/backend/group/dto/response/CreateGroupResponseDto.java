package org.triple.backend.group.dto.response;

import lombok.Builder;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;

@Builder
public record CreateGroupResponseDto(
        String name,
        String description,
        int memberLimit,
        GroupKind groupKind,
        String thumbNailUrl
) {

    public static CreateGroupResponseDto fromEntity(final Group group) {
        return CreateGroupResponseDto
                .builder()
                .name(group.getName())
                .description(group.getDescription())
                .memberLimit(group.getMemberLimit())
                .groupKind(group.getGroupKind())
                .thumbNailUrl(group.getThumbNailUrl())
                .build();
    }
}
