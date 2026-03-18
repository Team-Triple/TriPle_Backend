package org.triple.backend.transfer.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.triple.backend.auth.session.PublicUuidCodec;
import org.triple.backend.transfer.dto.request.TransferAdjustRequestDto;
import org.triple.backend.transfer.dto.request.TransferCreateRequestDto;
import org.triple.backend.transfer.dto.response.TransferAdjustResponseDto;
import org.triple.backend.transfer.dto.response.TransferCreateResponseDto;
import org.triple.backend.transfer.dto.response.TransferDetailResponseDto;
import org.triple.backend.transfer.exception.TransferErrorCode;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransferUserIdMapper {

    private final PublicUuidCodec publicUuidCodec;

    public TransferCreateRequestDto decryptRecipientUserIds(final TransferCreateRequestDto request) {
        return new TransferCreateRequestDto(
                request.accountNumber(),
                request.bankName(),
                request.accountHolder(),
                request.totalAmount(),
                decryptCreateMembers(request.members()),
                request.groupId(),
                request.travelItineraryId(),
                request.dueAt()
        );
    }

    public TransferAdjustRequestDto decryptRecipientUserIds(final TransferAdjustRequestDto request) {
        return new TransferAdjustRequestDto(
                request.accountNumber(),
                request.bankName(),
                request.accountHolder(),
                request.totalAmount(),
                decryptAdjustMembers(request.members())
        );
    }

    public TransferCreateResponseDto encryptRecipientUserIds(final TransferCreateResponseDto response) {
        return new TransferCreateResponseDto(
                response.transferId(),
                response.accountNumber(),
                response.bankName(),
                response.accountHolder(),
                response.totalAmount(),
                encryptCreateMembers(response.members())
        );
    }

    public TransferAdjustResponseDto encryptRecipientUserIds(final TransferAdjustResponseDto response) {
        return new TransferAdjustResponseDto(
                response.transferId(),
                response.accountNumber(),
                response.bankName(),
                response.accountHolder(),
                response.totalAmount(),
                encryptAdjustMembers(response.members()),
                response.transferStatus()
        );
    }

    public TransferDetailResponseDto encryptUserIds(final TransferDetailResponseDto response) {
        List<TransferDetailResponseDto.MemberDto> encryptedMembers = response.members().stream()
                .map(member -> new TransferDetailResponseDto.MemberDto(
                        encryptPublicUuid(member.id()),
                        member.name(),
                        member.avatar(),
                        member.amount(),
                        member.settled()
                ))
                .toList();
        return new TransferDetailResponseDto(
                response.accountNumber(),
                response.bankName(),
                response.accountHolder(),
                response.totalAmount(),
                encryptedMembers,
                response.remainingAmount(),
                response.isDone()
        );
    }

    private List<TransferCreateRequestDto.MemberDto> decryptCreateMembers(
            final List<TransferCreateRequestDto.MemberDto> members
    ) {
        return members.stream()
                .map(member -> new TransferCreateRequestDto.MemberDto(
                        publicUuidCodec.decryptOrThrow(member.id(), TransferErrorCode.RECIPIENT_USER_NOT_FOUND),
                        member.name(),
                        member.avatar(),
                        member.amount(),
                        member.settled()
                ))
                .toList();
    }

    private List<TransferAdjustRequestDto.MemberDto> decryptAdjustMembers(
            final List<TransferAdjustRequestDto.MemberDto> members
    ) {
        return members.stream()
                .map(member -> new TransferAdjustRequestDto.MemberDto(
                        publicUuidCodec.decryptOrThrow(member.id(), TransferErrorCode.RECIPIENT_USER_NOT_FOUND),
                        member.name(),
                        member.avatar(),
                        member.amount(),
                        member.settled()
                ))
                .toList();
    }

    private List<TransferCreateResponseDto.MemberDto> encryptCreateMembers(
            final List<TransferCreateResponseDto.MemberDto> members
    ) {
        return members.stream()
                .map(member -> new TransferCreateResponseDto.MemberDto(
                        encryptPublicUuid(member.id()),
                        member.name(),
                        member.avatar(),
                        member.amount(),
                        member.settled()
                ))
                .toList();
    }

    private List<TransferCreateResponseDto.MemberDto> encryptAdjustMembers(
            final List<TransferCreateResponseDto.MemberDto> members
    ) {
        return members.stream()
                .map(member -> new TransferCreateResponseDto.MemberDto(
                        encryptPublicUuid(member.id()),
                        member.name(),
                        member.avatar(),
                        member.amount(),
                        member.settled()
                ))
                .toList();
    }

    private String encryptPublicUuid(final String plainPublicUuid) {
        return publicUuidCodec.encrypt(plainPublicUuid);
    }
}
