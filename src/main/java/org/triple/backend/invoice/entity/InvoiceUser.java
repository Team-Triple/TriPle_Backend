package org.triple.backend.invoice.entity;

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
        name = "invoice_user",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_invoice_user_invoice_user",
                        columnNames = {"invoice_id", "user_id"}
                )
        }
)
public class InvoiceUser {

    @Id
    @Column(name = "invoice_user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "remain_amount", nullable = false)
    private BigDecimal remainAmount;


    public static InvoiceUser create(final Invoice invoice, final User user, final BigDecimal remainAmount) {
        return InvoiceUser.builder()
                .invoice(invoice)
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
