package org.triple.backend.invoice.dto.response;

import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record InvoiceDetailResponseDto(
        String title,
        BigDecimal totalAmount,
        LocalDateTime dueAt,
        String description,
        UserSummaryDto creator,
        List<InvoiceMemberDto> invoiceMembers,
        BigDecimal remainingAmount,
        boolean isDone
) {

    public record UserSummaryDto(
            Long userId,
            String nickname,
            String profileUrl
    ) {
        public static UserSummaryDto from(final User user) {
            return new UserSummaryDto(user.getId(), user.getNickname(), user.getProfileUrl());
        }
    }

    public record InvoiceMemberDto(
            Long userId,
            String nickname,
            String profileUrl,
            BigDecimal remainAmount
    ) {
        public static InvoiceMemberDto from(final InvoiceUser invoiceUser) {
            User user = invoiceUser.getUser();
            return new InvoiceMemberDto(
                    user.getId(),
                    user.getNickname(),
                    user.getProfileUrl(),
                    invoiceUser.getRemainAmount()
            );
        }
    }

    public static InvoiceDetailResponseDto from(
            final Invoice invoice,
            final BigDecimal remainingAmount,
            final boolean isDone
    ) {
        List<InvoiceMemberDto> invoiceMembers = invoice.getInvoiceUsers().stream()
                .sorted(Comparator.comparing(invoiceUser -> invoiceUser.getUser().getId()))
                .map(InvoiceMemberDto::from)
                .toList();

        return new InvoiceDetailResponseDto(
                invoice.getTitle(),
                invoice.getTotalAmount(),
                invoice.getDueAt(),
                invoice.getDescription(),
                UserSummaryDto.from(invoice.getCreator()),
                invoiceMembers,
                remainingAmount,
                isDone
        );
    }
}
