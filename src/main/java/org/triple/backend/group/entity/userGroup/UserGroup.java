package org.triple.backend.group.entity.userGroup;

import jakarta.persistence.*;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.user.entity.User;

import java.time.LocalDateTime;

@Entity
public class UserGroup {

    @Id
    @Column(name = "user_group_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Group group;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private JoinStatus joinStatus;

    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;
}