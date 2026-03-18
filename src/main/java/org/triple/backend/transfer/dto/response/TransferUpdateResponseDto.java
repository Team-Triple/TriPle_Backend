package org.triple.backend.transfer.dto.response;

import org.triple.backend.transfer.entity.Transfer;
import org.triple.backend.transfer.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferUpdateResponseDto(
        Long transferId,
        String title,
        String description,
        BigDecimal totalAmount,
        LocalDateTime dueAt,
        TransferStatus transferStatus,
        LocalDateTime updatedAt
) {

    public static TransferUpdateResponseDto from(Transfer transfer) {
        return new TransferUpdateResponseDto(
                transfer.getId(),
                transfer.getTitle(),
                transfer.getDescription(),
                transfer.getTotalAmount(),
                transfer.getDueAt(),
                transfer.getTransferStatus(),
                transfer.getUpdatedAt()
        );
    }
}
