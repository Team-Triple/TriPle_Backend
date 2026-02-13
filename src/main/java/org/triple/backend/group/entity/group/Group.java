package org.triple.backend.group.entity.group;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Group extends BaseEntity {

    @Id
    @Column(name = "group_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private GroupKind groupKind;

    @OneToMany(mappedBy = "group", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @Builder.Default
    private List<UserGroup> userGroups = new ArrayList<>();

    @OneToMany(mappedBy = "group")
    @Builder.Default
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
}