package org.triple.backend.transfer.dto.response;

import org.triple.backend.transfer.entity.Transfer;
import org.triple.backend.transfer.entity.TransferUser;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record TransferDetailResponseDto(
        String accountNumber,
        String bankName,
        String accountHolder,
        BigDecimal totalAmount,
        List<MemberDto> members,
        BigDecimal remainingAmount,
        boolean isDone
) {
    public record MemberDto(
            String id,
            String name,
            String avatar,
            BigDecimal amount,
            boolean settled
    ) {}

    public static TransferDetailResponseDto from(
            final Transfer transfer,
            final BigDecimal remainingAmount,
            final boolean isDone
    ) {
        List<MemberDto> members = transfer.getTransferUsers().stream()
                .sorted(Comparator.comparing(transferUser -> transferUser.getUser().getId()))
                .map(transferUser -> new MemberDto(
                        transferUser.getUser().getPublicUuid().toString(),
                        transferUser.getUser().getNickname(),
                        transferUser.getUser().getProfileUrl(),
                        transferUser.getRemainAmount(),
                        transferUser.getRemainAmount().compareTo(BigDecimal.ZERO) == 0
                ))
                .toList();

        return new TransferDetailResponseDto(
                transfer.getAccountNumber(),
                transfer.getBankName(),
                transfer.getAccountHolder(),
                transfer.getTotalAmount(),
                members,
                remainingAmount,
                isDone
        );
    }
}
