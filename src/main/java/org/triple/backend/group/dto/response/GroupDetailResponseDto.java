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
        Role role,
        List<RecentPhotoDto> recentPhotos,
        int travelCount,
        List<RecentTravelDto> recentTravels,
        List<RecentReviewDto> recentReviews
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
            String description,
            int memberCount,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {}

    public record RecentReviewDto(
            Long reviewId,
            String travelItineraryName,
            String content,
            String writerNickname,
            String imageUrl,
            int view,
            LocalDateTime createdAt
    ) {}

    public GroupDetailResponseDto(
            final List<UserDto> users,
            final String name,
            final String description,
            final GroupKind groupKind,
            final String thumbNailUrl,
            final int currentMemberCount,
            final int memberLimit,
            final Role role,
            final List<RecentPhotoDto> recentPhotos,
            final List<RecentTravelDto> recentTravels,
            final List<RecentReviewDto> recentReviews
    ) {
        this(
                users,
                name,
                description,
                groupKind,
                thumbNailUrl,
                currentMemberCount,
                memberLimit,
                role,
                recentPhotos,
                resolveTravelCount(recentTravels),
                recentTravels,
                recentReviews
        );
    }

    private static int resolveTravelCount(final List<RecentTravelDto> recentTravels) {
        if (recentTravels == null) {
            return 0;
        }
        return recentTravels.size();
    }

    public static GroupDetailResponseDto from(
            final List<UserGroup> userGroups,
            final Group group,
            final Role role,
            final List<RecentPhotoDto> recentPhotos,
            final int travelCount,
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
                role,
                recentPhotos,
                travelCount,
                recentTravels,
                recentReviews
        );
    }
}
