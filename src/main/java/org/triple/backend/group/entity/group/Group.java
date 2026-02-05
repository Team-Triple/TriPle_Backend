package org.triple.backend.group.entity.group;

import jakarta.persistence.*;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.user.entity.User;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "travel_group")
public class Group extends BaseEntity {

    @Id
    @Column(name = "group_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private GroupKind groupKind;

    @OneToMany(mappedBy = "group")
    private List<UserGroup> userGroups = new ArrayList<>();

    @OneToMany(mappedBy = "group")
    private List<JoinApply> joinApplies = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User owner;

    private String name;

    private String description;

    private String thumbnailUrl;

    private int currentMemberCount;

    private int memberLimit;
}
