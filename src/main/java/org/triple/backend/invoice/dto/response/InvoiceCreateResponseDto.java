package org.triple.backend.invoice.dto.response;

import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceUser;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceCreateResponseDto(
        Long invoiceId,
        Long groupId,
        Long travelItineraryId,
        String title,
        BigDecimal totalAmount,
        LocalDateTime dueAt,
        List<RecipientDto> recipients
) {

    public record RecipientDto(String userId, BigDecimal amount) {}


    public static InvoiceCreateResponseDto from(Invoice invoice, List<InvoiceUser> invoiceUsers) {
        List<RecipientDto> recipients = invoiceUsers.stream()
                .map(iu -> new RecipientDto(iu.getUser().getPublicUuid().toString(), iu.getRemainAmount()))
                .toList();

        return new InvoiceCreateResponseDto(
                invoice.getId(),
                invoice.getGroup().getId(),
                invoice.getTravelItinerary().getId(),
                invoice.getTitle(),
                invoice.getTotalAmount(),
                invoice.getDueAt(),
                recipients
        );
    }
}
