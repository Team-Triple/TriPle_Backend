package org.triple.backend.transfer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.triple.backend.user.entity.User;

import java.math.BigDecimal;

@Entity
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "transfer_user",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transfer_user_transfer_user",
                        columnNames = {"transfer_id", "user_id"}
                )
        }
)
public class TransferUser {

    @Id
    @Column(name = "transfer_user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "remain_amount", nullable = false)
    private BigDecimal remainAmount;


    public static TransferUser create(final Transfer transfer, final User user, final BigDecimal remainAmount) {
        return TransferUser.builder()
                .transfer(transfer)
                .user(user)
                .remainAmount(remainAmount)
                .build();
    }

    public void decreaseRemainAmount(BigDecimal amount) {
        BigDecimal newRemainAmount = remainAmount.subtract(amount);
        if(newRemainAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("잔액은 음수일 수 없습니다.");
        }
        this.remainAmount = newRemainAmount;
    }
}
