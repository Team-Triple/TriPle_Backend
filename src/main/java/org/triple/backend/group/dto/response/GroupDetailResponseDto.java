package org.triple.backend.group.dto.response;

import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;

import java.time.LocalDateTime;
import java.util.List;

public record GroupDetailResponseDto(
        List<UserDto> users,
        String name,
        String description,
        GroupKind groupKind,
        String thumbNailUrl,
        int currentMemberCount,
        int memberLimit,
        Boolean isOwner,
        List<RecentPhotoDto> photos,
        List<RecentTravelDto> travels,
        List<RecentReviewDto> reviews
) {

    public record UserDto(
            String name,
            String description,
            String profileUrl,
            Boolean isOwner
    ) {}

    public record RecentPhotoDto(
            Long imageId,
            String imageUrl
    ) {}

    public record RecentTravelDto(
            Long travelItineraryId,
            String title,
            String thumbnailUrl,
            String description,
            int memberCount,
            int memberLimit,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {}

    public record RecentReviewDto(
            Long reviewId,
            String content,
            String writerNickname
    ) {}

    public static GroupDetailResponseDto from(
            final List<UserGroup> userGroups,
            final Group group,
            final Role role,
            final List<RecentPhotoDto> recentPhotos,
            final List<RecentTravelDto> recentTravels,
            final List<RecentReviewDto> recentReviews
    ) {
        List<UserDto> users = userGroups.stream().map(ug -> new UserDto(
                ug.getUser().getNickname(),
                ug.getUser().getDescription(),
                ug.getUser().getProfileUrl(),
                ug.getRole() == Role.OWNER
        )).toList();

        return new GroupDetailResponseDto(
                users,
                group.getName(),
                group.getDescription(),
                group.getGroupKind(),
                group.getThumbNailUrl(),
                group.getCurrentMemberCount(),
                group.getMemberLimit(),
                role == Role.OWNER,
                recentPhotos,
                recentTravels,
                recentReviews
        );
    }
}
