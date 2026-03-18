package org.triple.backend.transfer.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.triple.backend.transfer.dto.RecipientAmountDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TransferCreateRequestDto(

        @NotBlank(message = "계좌번호는 필수입니다.")
        String accountNumber,

        @NotBlank(message = "은행명은 필수입니다.")
        String bankName,

        @NotBlank(message = "예금주명은 필수입니다.")
        String accountHolder,

        @NotNull(message = "총 청구 금액은 필수입니다.")
        @Positive(message = "총 청구 금액은 0보다 커야 합니다.")
        BigDecimal totalAmount,

        @NotEmpty(message = "정산 멤버 목록은 비어 있을 수 없습니다.")
        List<@Valid MemberDto> members,

        Long groupId,
        Long travelItineraryId,
        String title,
        String description,
        LocalDateTime dueAt
) {
    public List<RecipientAmountDto> recipients() {
        return members.stream()
                .map(member -> new RecipientAmountDto(member.id(), member.amount()))
                .toList();
    }

    public record MemberDto(
            @NotBlank(message = "멤버 ID는 필수입니다.")
            String id,

            @NotBlank(message = "멤버명은 필수입니다.")
            String name,

            @NotBlank(message = "아바타 URL은 필수입니다.")
            String avatar,

            @NotNull(message = "정산 금액은 필수입니다.")
            @PositiveOrZero(message = "정산 금액은 0 이상이어야 합니다.")
            BigDecimal amount,

            @NotNull(message = "정산 완료 여부는 필수입니다.")
            Boolean settled
    ) {
    }
}
