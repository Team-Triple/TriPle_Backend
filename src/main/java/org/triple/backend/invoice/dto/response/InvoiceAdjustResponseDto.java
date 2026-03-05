package org.triple.backend.invoice.dto.response;

import org.triple.backend.invoice.dto.RecipientAmountDto;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.entity.InvoiceUser;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceAdjustResponseDto(
        Long invoiceId,
        BigDecimal totalAmount,
        List<RecipientAmountDto> recipients,
        InvoiceStatus invoiceStatus
) {

    public static InvoiceAdjustResponseDto from(final Invoice invoice, final List<InvoiceUser> invoiceUsers) {
        List<RecipientAmountDto> recipients = invoiceUsers.stream()
                .map(invoiceUser -> new RecipientAmountDto(
                        invoiceUser.getUser().getPublicUuid().toString(),
                        invoiceUser.getRemainAmount()
                ))
                .toList();

        return new InvoiceAdjustResponseDto(
                invoice.getId(),
                invoice.getTotalAmount(),
                recipients,
                invoice.getInvoiceStatus()
        );
    }
}
