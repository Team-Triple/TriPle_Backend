package org.triple.backend.group.dto.response;

import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;

import java.util.List;

public record GroupUsersResponseDto(
    List<UserDto> users
) {

    public record UserDto(
            String id,
            String name,
            String description,
            String profileUrl,
            boolean isOwner
    ) {

    }

    public static GroupUsersResponseDto from(final List<UserGroup> userGroups) {
        return new GroupUsersResponseDto(userGroups.stream().map(ug -> new UserDto(
                ug.getUser().getPublicUuid().toString(),
                ug.getUser().getNickname(),
                ug.getUser().getDescription(),
                ug.getUser().getProfileUrl(),
                ug.getRole() == Role.OWNER
        )).toList());
    }
}
