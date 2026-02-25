package org.triple.backend.group.entity.userGroup;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "user_group",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_group_group_user",
                        columnNames = {"group_id", "user_id"}
                )
        }
)
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

    public static UserGroup create(final User user, final Group group, final Role role) {
        return UserGroup.builder()
                .user(user)
                .group(group)
                .role(role)
                .joinStatus(JoinStatus.JOINED)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    public void rejoin(final Role role) {
        if (this.joinStatus != JoinStatus.LEFTED) {
            throw new IllegalStateException("탈퇴 이력인 경우에만 재가입할 수 있습니다.");
        }
        this.role = role;
        this.joinStatus = JoinStatus.JOINED;
        this.joinedAt = LocalDateTime.now();
        this.leftAt = null;
    }

    public void leave() {
        if(this.joinStatus != JoinStatus.JOINED) {
            throw new IllegalStateException("가입된 그룹만 탈퇴할 수 있습니다.");
        }
        this.joinStatus = JoinStatus.LEFTED;
        this.leftAt = LocalDateTime.now();
    }
}
