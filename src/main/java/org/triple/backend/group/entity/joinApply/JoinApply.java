package org.triple.backend.group.entity.joinApply;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.user.entity.User;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "join_apply",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_join_apply_group_user", columnNames = {"group_id", "user_id"})
        }
)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JoinApply extends BaseEntity {

    @Id
    @Column(name = "join_apply_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Group group;

    @Enumerated(EnumType.STRING)
    private JoinApplyStatus joinApplyStatus;

    private LocalDateTime approvedAt;

    private LocalDateTime rejectedAt;

    private LocalDateTime canceledAt;

    public static JoinApply create(final User user, final Group group) {
        return JoinApply.builder()
                .user(user)
                .group(group)
                .joinApplyStatus(JoinApplyStatus.PENDING)
                .build();
    }

    public boolean isCanceled() {
        return this.joinApplyStatus == JoinApplyStatus.CANCELED;
    }

    public void reapply() {
        if (!isCanceled()) {
            throw new IllegalStateException("취소된 신청만 재신청할 수 있습니다.");
        }
        this.joinApplyStatus = JoinApplyStatus.PENDING;
        this.approvedAt = null;
        this.rejectedAt = null;
        this.canceledAt = null;
    }

    public void reject() {
        this.joinApplyStatus = JoinApplyStatus.REJECTED;
        this.rejectedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.joinApplyStatus = JoinApplyStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }
}
