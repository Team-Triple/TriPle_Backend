package org.triple.backend.invoice.dto.response;

import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoiceUpdateResponseDto(
        Long invoiceId,
        String title,
        String description,
        BigDecimal totalAmount,
        LocalDateTime dueAt,
        InvoiceStatus invoiceStatus,
        LocalDateTime updatedAt
) {

    public static InvoiceUpdateResponseDto from(Invoice invoice) {
        return new InvoiceUpdateResponseDto(
                invoice.getId(),
                invoice.getTitle(),
                invoice.getDescription(),
                invoice.getTotalAmount(),
                invoice.getDueAt(),
                invoice.getInvoiceStatus(),
                invoice.getUpdatedAt()
        );
    }
}
