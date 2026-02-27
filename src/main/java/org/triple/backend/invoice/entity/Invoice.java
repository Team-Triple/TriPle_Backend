package org.triple.backend.invoice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.payment.entity.Payment;
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
        name = "invoice",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_invoice_travel_itinerary",
                        columnNames = {"travel_itinerary_id"}
                )
        }
)
public class Invoice extends BaseEntity {

    @Id
    @Column(name = "invoice_id")
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
    @OneToMany(mappedBy = "invoice")
    private List<InvoiceUser> invoiceUsers = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "invoice")
    private List<Payment> payments = new ArrayList<>();

    private InvoiceStatus invoiceStatus;

    private String title;

    private BigDecimal totalAmount;

    private LocalDateTime dueAt;

    private String description;

    @Version
    private Long version;

    public static Invoice create(
            final String title,
            final String description,
            final BigDecimal totalAmount,
            final LocalDateTime dueAt,
            final User creator,
            final TravelItinerary travelItinerary,
            final Group group
    ) {
        return Invoice.builder()
                .title(title)
                .description(description)
                .totalAmount(totalAmount)
                .dueAt(dueAt)
                .group(group)
                .invoiceStatus(InvoiceStatus.UNCONFIRM)
                .creator(creator)
                .travelItinerary(travelItinerary)
                .build();
    }

    public boolean isCreatedBy(final Long userId) {
        return creator != null && creator.getId().equals(userId);
    }

    public boolean isDeletableStatus() {
        return invoiceStatus == InvoiceStatus.UNCONFIRM;
    }

    public void markDeleted() {
        this.invoiceStatus = InvoiceStatus.DELETED;
    }

    public void update(final String title, final String description, final LocalDateTime dueAt) {
        if(!invoiceStatus.equals(InvoiceStatus.UNCONFIRM)) {
            throw new IllegalStateException("청구서 수정이 불가합니다.");
        }
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
    }
}
