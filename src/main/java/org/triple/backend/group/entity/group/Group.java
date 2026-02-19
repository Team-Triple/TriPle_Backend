package org.triple.backend.group.entity.group;

import jakarta.persistence.*;
import lombok.*;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.user.entity.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "travel_group")
@Builder(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group extends BaseEntity {

    @Id
    @Column(name = "group_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private GroupKind groupKind;

    @Builder.Default
    @OneToMany(mappedBy = "group", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private List<UserGroup> userGroups = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "group")
    private List<JoinApply> joinApplies = new ArrayList<>();

    private String name;

    private String description;

    private String thumbNailUrl;

    @Builder.Default
    private int currentMemberCount = 1;

    private int memberLimit;

    public void addMember(User user, Role role) {
        UserGroup userGroup = UserGroup.builder()
                .user(user)
                .group(this)
                .role(role)
                .joinStatus(JoinStatus.JOINED)
                .joinedAt(LocalDateTime.now())
                .build();

        this.userGroups.add(userGroup);
        user.getUserGroups().add(userGroup);
    }

    public static Group create(final GroupKind kind, final String name, final String description, final String thumbNailUrl, int memberLimit) {
        return Group.builder()
                .name(validateName(name))
                .description(validateDescription(description))
                .thumbNailUrl(thumbNailUrl)
                .groupKind(validateKind(kind))
                .memberLimit(validateMemberLimit(memberLimit))
                .currentMemberCount(1)
                .build();
    }

    private static GroupKind validateKind(final GroupKind kind) {
        if (kind == null) throw new IllegalArgumentException("그룹 종류는 null값일 수 없습니다.");
        return kind;
    }

    private static String validateDescription(final String description) {
        if(description == null || description.isBlank()) throw new IllegalArgumentException("그룹 설명은 null값 혹은 빈값일 수 없습니다.");
        return description;
    }

    private static String validateName(final String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("그룹 이름은 null값 혹은 빈값일 수 없습니다.");
        return name;
    }

    private static int validateMemberLimit(final int memberLimit) {
        if (memberLimit < 1 || memberLimit > 50) throw new IllegalArgumentException("그룹 멤버 제한 수는 1명 이하 혹은 50명 이상이 될 수 없습니다.");
        return memberLimit;
    }
}