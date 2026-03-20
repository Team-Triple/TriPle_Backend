package org.triple.backend.transfer.dto.response;

import org.triple.backend.transfer.entity.Transfer;
import org.triple.backend.transfer.entity.TransferStatus;
import org.triple.backend.transfer.entity.TransferUser;

import java.math.BigDecimal;
import java.util.List;

public record TransferAdjustResponseDto(
        Long transferId,
        String accountNumber,
        String bankName,
        String accountHolder,
        BigDecimal totalAmount,
        List<TransferCreateResponseDto.MemberDto> members,
        TransferStatus transferStatus
) {

    public static TransferAdjustResponseDto from(final Transfer transfer, final List<TransferUser> transferUsers) {
        List<TransferCreateResponseDto.MemberDto> members = transferUsers.stream()
                .map(transferUser -> new TransferCreateResponseDto.MemberDto(
                        transferUser.getUser().getPublicUuid().toString(),
                        transferUser.getUser().getNickname(),
                        transferUser.getUser().getProfileUrl(),
                        transferUser.getRemainAmount(),
                        transferUser.getRemainAmount().compareTo(BigDecimal.ZERO) == 0
                ))
                .toList();

        return new TransferAdjustResponseDto(
                transfer.getId(),
                transfer.getAccountNumber(),
                transfer.getBankName(),
                transfer.getAccountHolder(),
                transfer.getTotalAmount(),
                members,
                transfer.getTransferStatus()
        );
    }
}
