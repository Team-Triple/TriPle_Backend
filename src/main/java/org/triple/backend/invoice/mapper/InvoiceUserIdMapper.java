package org.triple.backend.invoice.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.triple.backend.auth.session.PublicUuidCodec;
import org.triple.backend.invoice.dto.RecipientAmountDto;
import org.triple.backend.invoice.dto.request.InvoiceAdjustRequestDto;
import org.triple.backend.invoice.dto.request.InvoiceCreateRequestDto;
import org.triple.backend.invoice.dto.response.InvoiceAdjustResponseDto;
import org.triple.backend.invoice.dto.response.InvoiceCreateResponseDto;
import org.triple.backend.invoice.dto.response.InvoiceDetailResponseDto;
import org.triple.backend.invoice.exception.InvoiceErrorCode;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InvoiceUserIdMapper {

    private final PublicUuidCodec publicUuidCodec;

    public InvoiceCreateRequestDto decryptRecipientUserIds(final InvoiceCreateRequestDto request) {
        return new InvoiceCreateRequestDto(
                request.groupId(),
                request.travelItineraryId(),
                decryptRecipientUserIds(request.recipients()),
                request.title(),
                request.description(),
                request.totalAmount(),
                request.dueAt()
        );
    }

    public InvoiceAdjustRequestDto decryptRecipientUserIds(final InvoiceAdjustRequestDto request) {
        return new InvoiceAdjustRequestDto(
                request.totalAmount(),
                decryptRecipientUserIds(request.recipients())
        );
    }

    public InvoiceCreateResponseDto encryptRecipientUserIds(final InvoiceCreateResponseDto response) {
        return new InvoiceCreateResponseDto(
                response.invoiceId(),
                response.groupId(),
                response.travelItineraryId(),
                response.title(),
                response.totalAmount(),
                response.dueAt(),
                encryptCreateRecipients(response.recipients())
        );
    }

    public InvoiceAdjustResponseDto encryptRecipientUserIds(final InvoiceAdjustResponseDto response) {
        return new InvoiceAdjustResponseDto(
                response.invoiceId(),
                response.totalAmount(),
                encryptAdjustRecipients(response.recipients()),
                response.invoiceStatus()
        );
    }

    public InvoiceDetailResponseDto encryptUserIds(final InvoiceDetailResponseDto response) {
        InvoiceDetailResponseDto.UserSummaryDto encryptedCreator = new InvoiceDetailResponseDto.UserSummaryDto(
                encryptPublicUuid(response.creator().userId()),
                response.creator().nickname(),
                response.creator().profileUrl()
        );
        List<InvoiceDetailResponseDto.InvoiceMemberDto> encryptedMembers = response.invoiceMembers().stream()
                .map(member -> new InvoiceDetailResponseDto.InvoiceMemberDto(
                        encryptPublicUuid(member.userId()),
                        member.nickname(),
                        member.profileUrl(),
                        member.remainAmount()
                ))
                .toList();
        return new InvoiceDetailResponseDto(
                response.title(),
                response.totalAmount(),
                response.dueAt(),
                response.description(),
                encryptedCreator,
                encryptedMembers,
                response.remainingAmount(),
                response.isDone()
        );
    }

    private List<RecipientAmountDto> decryptRecipientUserIds(final List<RecipientAmountDto> recipients) {
        return recipients.stream().map(this::decryptRecipientUserId).toList();
    }

    private RecipientAmountDto decryptRecipientUserId(final RecipientAmountDto recipient) {
        return new RecipientAmountDto(
                publicUuidCodec.decryptOrThrow(recipient.userId(), InvoiceErrorCode.RECIPIENT_USER_NOT_FOUND),
                recipient.amount()
        );
    }

    private List<InvoiceCreateResponseDto.RecipientDto> encryptCreateRecipients(
            final List<InvoiceCreateResponseDto.RecipientDto> recipients
    ) {
        return recipients.stream()
                .map(recipient -> new InvoiceCreateResponseDto.RecipientDto(
                        encryptPublicUuid(recipient.userId()),
                        recipient.amount()
                ))
                .toList();
    }

    private List<RecipientAmountDto> encryptAdjustRecipients(final List<RecipientAmountDto> recipients) {
        return recipients.stream()
                .map(recipient -> new RecipientAmountDto(
                        encryptPublicUuid(recipient.userId()),
                        recipient.amount()
                ))
                .toList();
    }

    private String encryptPublicUuid(final String plainPublicUuid) {
        return publicUuidCodec.encrypt(plainPublicUuid);
    }
}
