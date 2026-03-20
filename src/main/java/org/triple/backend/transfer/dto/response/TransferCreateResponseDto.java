package org.triple.backend.transfer.dto.response;

import org.triple.backend.transfer.entity.Transfer;
import org.triple.backend.transfer.entity.TransferUser;

import java.math.BigDecimal;
import java.util.List;

public record TransferCreateResponseDto(
        Long transferId,
        String accountNumber,
        String bankName,
        String accountHolder,
        BigDecimal totalAmount,
        List<MemberDto> members
) {

    public record MemberDto(
            String id,
            String name,
            String avatar,
            BigDecimal amount,
            boolean settled
    ) {
    }


    public static TransferCreateResponseDto from(final Transfer transfer, final List<TransferUser> transferUsers) {
        List<MemberDto> members = transferUsers.stream()
                .map(transferUser -> new MemberDto(
                        transferUser.getUser().getPublicUuid().toString(),
                        transferUser.getUser().getNickname(),
                        transferUser.getUser().getProfileUrl(),
                        transferUser.getRemainAmount(),
                        transferUser.getRemainAmount().compareTo(BigDecimal.ZERO) == 0
                ))
                .toList();

        return new TransferCreateResponseDto(
                transfer.getId(),
                transfer.getAccountNumber(),
                transfer.getBankName(),
                transfer.getAccountHolder(),
                transfer.getTotalAmount(),
                members
        );
    }
}
