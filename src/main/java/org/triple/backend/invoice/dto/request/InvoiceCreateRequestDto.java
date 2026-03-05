package org.triple.backend.invoice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.triple.backend.invoice.dto.RecipientAmountDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceCreateRequestDto(

        @NotNull(message = "그룹 ID는 필수입니다.")
        Long groupId,

        @NotNull(message = "여행 일정 ID는 필수입니다.")
        Long travelItineraryId,

        @NotEmpty(message = "청구 대상 목록은 비어 있을 수 없습니다.")
        List<@Valid RecipientAmountDto> recipients,

        @NotBlank(message = "청구서 제목은 필수입니다.")
        @Size(max = 50, message = "청구서 제목은 최대 50자까지 입력할 수 있습니다.")
        String title,

        @NotBlank(message = "청구서 설명은 필수입니다.")
        String description,

        @NotNull(message = "총 청구 금액은 필수입니다.")
        @Positive(message = "총 청구 금액은 0보다 커야 합니다.")
        BigDecimal totalAmount,

        @NotNull(message = "납부 기한은 필수입니다.")
        @Future(message = "납부 기한은 현재 시각 이후여야 합니다.")
        LocalDateTime dueAt
) {
}
