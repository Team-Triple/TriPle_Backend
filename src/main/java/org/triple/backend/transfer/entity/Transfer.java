package org.triple.backend.transfer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "transfer",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transfer_travel_itinerary",
                        columnNames = {"travel_itinerary_id"}
                )
        }
)
public class Transfer extends BaseEntity {

    @Id
    @Column(name = "transfer_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_user_id")
    private User creator;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_itinerary_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private TravelItinerary travelItinerary;

    @Builder.Default
    @OneToMany(mappedBy = "transfer")
    private List<TransferUser> transferUsers = new ArrayList<>();

    private TransferStatus transferStatus;

    private BigDecimal totalAmount;

    private LocalDateTime dueAt;

    private String accountNumber;

    private String bankName;

    private String accountHolder;

    @Version
    private Long version;

    public static Transfer create(
            final String accountNumber,
            final String bankName,
            final String accountHolder,
            final BigDecimal totalAmount,
            final LocalDateTime dueAt,
            final User creator,
            final TravelItinerary travelItinerary,
            final Group group
    ) {
        return Transfer.builder()
                .accountNumber(accountNumber)
                .bankName(bankName)
                .accountHolder(accountHolder)
                .totalAmount(totalAmount)
                .dueAt(dueAt)
                .group(group)
                .transferStatus(TransferStatus.UNCONFIRM)
                .creator(creator)
                .travelItinerary(travelItinerary)
                .build();
    }

    public boolean isCreatedBy(final Long userId) {
        return creator != null && creator.getId().equals(userId);
    }

    public boolean isDeletableStatus() {
        return transferStatus == TransferStatus.UNCONFIRM;
    }

    public void markDeleted() {
        this.transferStatus = TransferStatus.DELETED;
    }

    public void update(final LocalDateTime dueAt) {
        if(!transferStatus.equals(TransferStatus.UNCONFIRM)) {
            throw new IllegalStateException("청구서 수정이 불가합니다.");
        }
        this.dueAt = dueAt;
    }

    public void updateAmount(final BigDecimal totalAmount) {
        updateSettlementInfo(accountNumber, bankName, accountHolder, totalAmount);
    }

    public void updateSettlementInfo(
            final String accountNumber,
            final String bankName,
            final String accountHolder,
            final BigDecimal totalAmount
    ) {
        if (transferStatus != TransferStatus.UNCONFIRM) {
            throw new IllegalStateException("확정되지 않은 청구서만 수정할 수 있습니다.");
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("계좌번호는 필수입니다.");
        }
        if (bankName == null || bankName.isBlank()) {
            throw new IllegalArgumentException("은행명은 필수입니다.");
        }
        if (accountHolder == null || accountHolder.isBlank()) {
            throw new IllegalArgumentException("예금주명은 필수입니다.");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("총 금액은 0보다 커야 합니다.");
        }

        this.accountNumber = accountNumber;
        this.bankName = bankName;
        this.accountHolder = accountHolder;
        this.totalAmount = totalAmount;
    }

    public void confirm() {
        if(transferStatus != TransferStatus.UNCONFIRM) {
            throw new IllegalStateException("확정되지 않은 청구서만 확정지을 수 있습니다.");
        }

        this.transferStatus = TransferStatus.CONFIRM;
    }

    public void markDone() {
        if (transferStatus != TransferStatus.CONFIRM) {
            throw new IllegalStateException("확정된 청구서만 완료 처리할 수 있습니다.");
        }

        this.transferStatus = TransferStatus.DONE;
    }

    public boolean isAllPaid() {
        return transferUsers.stream()
                .allMatch(TransferUser::isSettled);
    }
}
