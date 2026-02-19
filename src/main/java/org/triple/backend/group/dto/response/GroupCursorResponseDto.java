package org.triple.backend.group.dto.response;

import org.triple.backend.group.entity.group.Group;

import java.util.List;

public record GroupCursorResponseDto(
    List<GroupSummaryDto> items,
    Long nextCursor,
    boolean hasNext

) {

    public record GroupSummaryDto(
        Long groupId,
        String name,
        String description,
        int currentMemberCount,
        int memberLimit,
        String thumbNailUrl
    ){

    }

    public static GroupCursorResponseDto from(final List<Group> rows, final Long nextCursor, final boolean hasNext) {

        List<GroupSummaryDto> items = rows.stream().map(v ->
                new GroupSummaryDto(v.getId(), v.getName(),
                        v.getDescription(), v.getCurrentMemberCount(), v.getMemberLimit(), v.getThumbNailUrl()))
                .toList();

        return new GroupCursorResponseDto(items, nextCursor, hasNext);
    }
}
