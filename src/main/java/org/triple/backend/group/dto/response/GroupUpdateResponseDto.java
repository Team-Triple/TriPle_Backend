package org.triple.backend.group.dto.response;

import org.triple.backend.group.entity.group.GroupKind;

public record GroupUpdateResponseDto(
        Long groupId,
        GroupKind groupKind,
        String name,
        String description,
        String thumbNailUrl,
        int memberLimit,
        int currentMemberCount

) {
}
