package org.triple.backend.group.dto.response;

import org.triple.backend.group.entity.joinApply.JoinApplyStatus;

import java.util.List;

public record JoinApplyUserResponseDto(
        List<UserDto> users,
        Long nextCursor,
        boolean hasNext
) {

    public record UserDto(
            Long joinApplyId,
            String nickname,
            String description,
            String profileUrl,
            JoinApplyStatus status
    ) {
    }
}
