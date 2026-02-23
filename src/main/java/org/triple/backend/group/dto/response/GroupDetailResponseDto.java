package org.triple.backend.group.dto.response;

import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;

import java.util.List;

public record GroupDetailResponseDto(
    List<UserDto> users,
    String name,
    String description,
    GroupKind groupKind,
    String thumbNailUrl,
    int currentMemberCount,
    int memberLimit
) {

    public record UserDto(
            String name,
            String description,
            String profileUrl,
            Boolean isOwner
    ) {

    }

    public static GroupDetailResponseDto from(final List<UserGroup> userGroups, final Group group) {
        List<UserDto> users= userGroups.stream().map(ug -> new UserDto(
                ug.getUser().getNickname(),
                ug.getUser().getDescription(),
                ug.getUser().getProfileUrl(),
                ug.getRole().equals(Role.OWNER)
        )).toList();

        return new GroupDetailResponseDto(
                users,
                group.getName(),
                group.getDescription(),
                group.getGroupKind(),
                group.getThumbNailUrl(),
                group.getCurrentMemberCount(),
                group.getMemberLimit()
        );
    }
}
