package org.triple.backend.group.dto.response;

import org.triple.backend.group.entity.userGroup.Role;

public record GroupMenuResponseDto(
        String name,
        String description,
        int currentMemberCount,
        int memberLimit,
        Role role
) {
}
