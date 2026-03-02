package org.triple.backend.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.invoice.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
public class Payment extends BaseEntity {

    @Id
    @Column(name = "payment_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    private String pgProvider;

    private String method;

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

    public boolean isStatus(PaymentStatus status) {
        return paymentStatus.equals(status);
    }

    public boolean isApprovedAmount(BigDecimal approvedAmount) {
        return this.approvedAmount.equals(approvedAmount);
    }

    public void updateStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public void updateReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
    }

    public void updatePaymentKey(String paymentKey) {
        this.paymentKey = paymentKey;
    }
}
