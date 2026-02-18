package org.triple.backend.travel.entity;

import jakarta.persistence.*;
import lombok.*;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.travel.dto.request.TravelSaveRequestDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelItinerary extends BaseEntity {

    @Id
    @Column(name = "travel_itinerary_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Group group;

    private String description;

    private String thumbnailUrl;

    private int memberLimit;

    private int memberCount = 1;

    private boolean isDeleted;

    @OneToMany(mappedBy = "travelItinerary")
    private List<UserTravelItinerary> userTravelItineraries = new ArrayList<>();

    @Builder(access = AccessLevel.PROTECTED)
    public TravelItinerary(
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Group group,
            String description,
            String thumbnailUrl,
            int memberLimit,
            int memberCount,
            boolean isDeleted
    ) {
        this.title = validateTitle(title);
        this.startAt = validateStartAt(startAt);
        this.endAt = validateEndAt(endAt);
        validateDateOrder(this.startAt, this.endAt);
        this.group = validateGroup(group);
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.memberLimit = validateMemberLimit(memberLimit);
        this.memberCount = memberCount;
        this.isDeleted = isDeleted;
    }

    //첫 생성 정적 팩터리 메서드
    public static TravelItinerary of(TravelSaveRequestDto travelsRequestDto, Group group) {
        return TravelItinerary.builder()
                .title(travelsRequestDto.title())
                .startAt(travelsRequestDto.startAt())
                .endAt(travelsRequestDto.endAt())
                .group(group)
                .description(travelsRequestDto.description())
                .thumbnailUrl(travelsRequestDto.thumbnailUrl())
                .memberLimit(travelsRequestDto.memberLimit())
                .memberCount(1)
                .isDeleted(false)
                .build();
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("제목은 빈값일 수 없습니다.");
        return title;
    }

    private static LocalDateTime validateStartAt(LocalDateTime startAt) {
        if (startAt == null) throw new IllegalArgumentException("startAt은 Null일 수 없습니다.");
        return startAt;
    }

    private static LocalDateTime validateEndAt(LocalDateTime endAt) {
        if (endAt == null) throw new IllegalArgumentException("endAt은 Null일 수 없습니다.");
        return endAt;
    }

    private static void validateDateOrder(LocalDateTime startAt, LocalDateTime endAt) {
        if (!startAt.isBefore(endAt)) throw new IllegalArgumentException("startAt이 endAt보다 작아야 합니다.");
    }

    private static Group validateGroup(Group group) {
        if (group == null) throw new IllegalArgumentException("group이 Null일 수 없습니다.");
        return group;
    }

    private static int validateMemberLimit(int memberLimit) {
        if (memberLimit < 1 || memberLimit > 20) throw new IllegalArgumentException("멤버 제한은 0보다 크거나, 21보다 작아야 합니다.");
        return memberLimit;
    }
}