package org.triple.backend.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "payment",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_order_id",
                        columnNames = {"order_id"}
                )
        }
)
public class Payment extends BaseEntity {

    @Id
    @Column(name = "payment_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    private PgProvider pgProvider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String name;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Column(name = "order_id")
    private String orderId;

    private String paymentKey;

    private BigDecimal requestedAmount;

    private BigDecimal approvedAmount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    private LocalDateTime approvedAt;

    private LocalDateTime requestedAt;

    private String receiptUrl;

    private String failureCode;

    private String failureMessage;


    public static Payment create(
            final Invoice invoice,
            final User user,
            final String name,
            final PgProvider pgProvider,
            final PaymentMethod method,
            final String orderId,
            final BigDecimal requestedAmount
    ) {

        validateInvoice(invoice);
        validateUser(user);
        validatePgProvider(pgProvider);
        validateOrderId(orderId);
        validateRequestedAmount(requestedAmount);

        return Payment.builder()
                .invoice(invoice)
                .user(user)
                .name(name)
                .pgProvider(pgProvider)
                .method(method)
                .orderId(orderId)
                .requestedAmount(requestedAmount)
                .paymentStatus(PaymentStatus.READY)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    private static void validateInvoice(final Invoice invoice) {
        if (invoice == null) {
            throw new IllegalArgumentException("청구서는 필수입니다.");
        }

        if(invoice.getInvoiceStatus() != InvoiceStatus.CONFIRM) {
            throw new IllegalArgumentException("청구서의 상태가 CONFIRM이어야 합니다.");
        }
    }

    private static void validateUser(final User user) {
        if (user == null) {
            throw new IllegalArgumentException("결제 사용자는 필수입니다.");
        }
    }

    private static void validatePgProvider(final PgProvider pgProvider) {
        if (pgProvider == null) {
            throw new IllegalArgumentException("PG사는 필수입니다.");
        }
    }

    private static void validateOrderId(final String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("주문 ID는 필수입니다.");
        }
    }

    private static void validateRequestedAmount(final BigDecimal requestedAmount) {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("요청 금액은 0보다 커야 합니다.");
        }
    }
}
