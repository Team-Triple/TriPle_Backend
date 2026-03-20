package org.triple.backend.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.transfer.dto.request.TransferAdjustRequestDto;
import org.triple.backend.transfer.dto.request.TransferCreateRequestDto;
import org.triple.backend.transfer.dto.request.TransferUpdateRequestDto;
import org.triple.backend.transfer.dto.response.TransferAdjustResponseDto;
import org.triple.backend.transfer.dto.response.TransferCreateResponseDto;
import org.triple.backend.transfer.dto.response.TransferDetailResponseDto;
import org.triple.backend.transfer.dto.response.TransferUpdateResponseDto;
import org.triple.backend.transfer.entity.Transfer;
import org.triple.backend.transfer.entity.TransferStatus;
import org.triple.backend.transfer.entity.TransferUser;
import org.triple.backend.transfer.exception.TransferErrorCode;
import org.triple.backend.transfer.repository.TransferJpaRepository;
import org.triple.backend.transfer.repository.TransferUserJpaRepository;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.exception.TravelItineraryErrorCode;
import org.triple.backend.travel.exception.UserTravelItineraryErrorCode;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.service.UserFinder;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferJpaRepository transferJpaRepository;
    private final GroupJpaRepository groupJpaRepository;
    private final TravelItineraryJpaRepository travelItineraryJpaRepository;
    private final UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;
    private final TransferUserJpaRepository transferUserJpaRepository;
    private final UserGroupJpaRepository userGroupJpaRepository;
    private final UserFinder userFinder;

    @Transactional
    public TransferCreateResponseDto create(final Long userId, final TransferCreateRequestDto dto) {
        validateMembers(
                dto.members(),
                dto.totalAmount(),
                TransferCreateRequestDto.MemberDto::settled,
                TransferCreateRequestDto.MemberDto::amount
        );

        Group group = groupJpaRepository.findByIdAndIsDeletedFalse(dto.groupId()).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        TravelItinerary travelItinerary = travelItineraryJpaRepository.findByIdAndGroupIdAndIsDeletedFalseForUpdate(dto.travelItineraryId(), dto.groupId()).orElseThrow(() -> new BusinessException(TravelItineraryErrorCode.TRAVEL_NOT_FOUND));

        UserTravelItinerary creatorTravelMembership = userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(userId, travelItinerary.getId())
                .orElseThrow(() -> new BusinessException(TransferErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND));

        if (!creatorTravelMembership.getUserRole().equals(UserRole.LEADER)) {
            throw new BusinessException(TransferErrorCode.NOT_TRAVEL_LEADER);
        }

        User creator = creatorTravelMembership.getUser();

        Map<Long, TransferCreateRequestDto.MemberDto> memberByUserId = toCreateMemberMap(dto.members());
        Map<Long, User> userById = loadRecipientUsersOrThrow(dto.groupId(), memberByUserId.keySet());

        Transfer savedTransfer = saveTransferOrThrow(dto, creator, travelItinerary, group);
        List<TransferUser> transferUsers = createTransferUsers(savedTransfer, memberByUserId, userById);
        saveTransferUsersOrThrow(transferUsers);

        return TransferCreateResponseDto.from(savedTransfer, transferUsers);
    }

    @Transactional(readOnly = true)
    public TransferDetailResponseDto searchTransfer(final Long userId, final Long travelItineraryId) {
        if (!userTravelItineraryJpaRepository.existsByUserIdAndTravelItineraryId(userId, travelItineraryId)) {
            throw new BusinessException(TransferErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND);
        }

        Transfer transfer = transferJpaRepository
                .findTransferDetailByTravelItineraryIdAndTransferStatusNot(travelItineraryId, TransferStatus.DELETED)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE));

        BigDecimal remainingAmount = transfer.getTransferUsers().stream()
                .map(TransferUser::getRemainAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean isDone = remainingAmount.compareTo(BigDecimal.ZERO) == 0;

        return TransferDetailResponseDto.from(transfer, remainingAmount, isDone);
    }

    @Transactional
    public TransferUpdateResponseDto updateMetaInfo(final Long userId, final Long transferId, final TransferUpdateRequestDto dto) {
        Transfer transfer = transferJpaRepository.findByIdForUpdateWithGroupAndTravelItinerary(transferId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE));
        validateStatusOrThrow(transfer, TransferErrorCode.INVOICE_UPDATE_NOT_ALLOWED_STATUS);
        validateLeaderAuthorityOrThrow(userId, transfer);

        transfer.update(dto.dueAt());

        try {
            transferJpaRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(TransferErrorCode.CONCURRENT_INVOICE_UPDATE);
        }

        return TransferUpdateResponseDto.from(transfer);
    }

    @Transactional
    public TransferAdjustResponseDto updateInfo(final Long userId, final Long transferId, final TransferAdjustRequestDto dto) {

        Transfer transfer = transferJpaRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE));

        validateStatusOrThrow(transfer, TransferErrorCode.INVOICE_UPDATE_NOT_ALLOWED_STATUS);
        validateLeaderAuthorityOrThrow(userId, transfer);

        Map<Long, TransferAdjustRequestDto.MemberDto> memberByUserId = toAdjustMemberMap(dto.members());
        validateMembers(
                dto.members(),
                dto.totalAmount(),
                TransferAdjustRequestDto.MemberDto::settled,
                TransferAdjustRequestDto.MemberDto::amount
        );
        Map<Long, User> userById = loadRecipientUsersOrThrow(transfer.getGroup().getId(), memberByUserId.keySet());

        transferUserJpaRepository.deleteAllByTransferIdInBatch(transferId);
        transfer.updateSettlementInfo(
                dto.accountNumber(),
                dto.bankName(),
                dto.accountHolder(),
                dto.totalAmount()
        );

        List<TransferUser> transferUsers = memberByUserId.entrySet().stream()
                .map(entry -> TransferUser.create(
                        transfer,
                        userById.get(entry.getKey()),
                        resolveRemainAmount(entry.getValue().amount(), entry.getValue().settled())
                ))
                .toList();

        transferUserJpaRepository.saveAll(transferUsers);

        return TransferAdjustResponseDto.from(transfer, transferUsers);
    }

    private void validateStatusOrThrow(final Transfer transfer, final TransferErrorCode errorCode) {
        if (transfer.getTransferStatus() != TransferStatus.UNCONFIRM) {
            throw new BusinessException(errorCode);
        }
    }

    private void validateLeaderAuthorityOrThrow(final Long userId, final Transfer transfer) {
        if (!userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(transfer.getGroup().getId(), userId, JoinStatus.JOINED)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        UserTravelItinerary userTravelItinerary = userTravelItineraryJpaRepository
                .findByUserIdAndTravelItineraryId(userId, transfer.getTravelItinerary().getId())
                .orElseThrow(() -> new BusinessException(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND));

        if (userTravelItinerary.getUserRole() != UserRole.LEADER) {
            throw new BusinessException(TransferErrorCode.NOT_TRAVEL_LEADER);
        }
    }

    private Map<Long, TransferCreateRequestDto.MemberDto> toCreateMemberMap(
            final List<TransferCreateRequestDto.MemberDto> members
    ) {
        return members.stream()
                .collect(Collectors.toMap(
                        member -> resolveRecipientUserIdOrThrow(member.id()),
                        member -> member,
                        (a,b) -> {throw new BusinessException(TransferErrorCode.DUPLICATE_RECIPIENT);},
                        LinkedHashMap::new
                ));
    }

    private Map<Long, TransferAdjustRequestDto.MemberDto> toAdjustMemberMap(
            final List<TransferAdjustRequestDto.MemberDto> members
    ) {
        return members.stream()
                .collect(Collectors.toMap(
                        member -> resolveRecipientUserIdOrThrow(member.id()),
                        member -> member,
                        (a,b) -> {throw new BusinessException(TransferErrorCode.DUPLICATE_RECIPIENT);},
                        LinkedHashMap::new
                ));
    }

    private Long resolveRecipientUserIdOrThrow(final String recipientUserId) {
        return userFinder.findIdByPublicUuidOrThrow(recipientUserId, TransferErrorCode.RECIPIENT_USER_NOT_FOUND);
    }

    private Map<Long, User> loadRecipientUsersOrThrow(
            final Long groupId,
            final Collection<Long> recipientUserIds
    ) {
        List<Long> recipientUserIdList = new ArrayList<>(recipientUserIds);
        List<UserGroup> lockedUserGroups = userGroupJpaRepository.findJoinedUsersInGroupForUpdate(groupId, JoinStatus.JOINED, recipientUserIdList);
        if (lockedUserGroups.size() != recipientUserIdList.size()) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        return lockedUserGroups.stream().map(UserGroup::getUser).collect(Collectors.toMap(User::getId, user -> user));
    }

    private Transfer saveTransferOrThrow(
            final TransferCreateRequestDto dto,
            final User owner,
            final TravelItinerary travelItinerary,
            final Group group
    ) {
        try {
            return transferJpaRepository.saveAndFlush(
                    Transfer.create(
                            dto.accountNumber(),
                            dto.bankName(),
                            dto.accountHolder(),
                            dto.totalAmount(),
                            dto.dueAt(),
                            owner,
                            travelItinerary,
                            group
                    )
            );
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(TransferErrorCode.DUPLICATE_INVOICE);
        }
    }

    private List<TransferUser> createTransferUsers(
            final Transfer savedTransfer,
            final Map<Long, TransferCreateRequestDto.MemberDto> memberByUserId,
            final Map<Long, User> userById
    ) {
        return memberByUserId.entrySet().stream()
                .map(entry -> TransferUser.create(
                        savedTransfer,
                        userById.get(entry.getKey()),
                        resolveRemainAmount(entry.getValue().amount(), entry.getValue().settled())
                ))
                .toList();
    }

    private BigDecimal resolveRemainAmount(final BigDecimal amount, final boolean settled) {
        if (settled) {
            return BigDecimal.ZERO;
        }
        return amount;
    }

    private void saveTransferUsersOrThrow(final List<TransferUser> transferUsers) {
        try {
            transferUserJpaRepository.saveAll(transferUsers);
            transferUserJpaRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(TransferErrorCode.DUPLICATE_RECIPIENT);
        }
    }

    private <M> void validateMembers(
            final List<M> members,
            final BigDecimal totalAmount,
            final Function<M, Boolean> settledExtractor,
            final Function<M, BigDecimal> amountExtractor
    ) {
        validateSettledAmountConsistency(
                members,
                settledExtractor,
                amountExtractor
        );
        validateTotalAmount(members, totalAmount, amountExtractor);
    }

    private <M> void validateTotalAmount(
            final List<M> members,
            final BigDecimal totalAmount,
            final Function<M, BigDecimal> amountExtractor
    ) {
        BigDecimal sum = members.stream()
                .map(amountExtractor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(totalAmount) != 0) {
            throw new BusinessException(TransferErrorCode.INVALID_TOTAL_AMOUNT);
        }
    }

    private <M> void validateSettledAmountConsistency(
            final List<M> members,
            final Function<M, Boolean> settledExtractor,
            final Function<M, BigDecimal> amountExtractor
    ) {
        boolean hasInvalidMember = members.stream()
                .anyMatch(member -> Boolean.TRUE.equals(settledExtractor.apply(member))
                        && amountExtractor.apply(member).compareTo(BigDecimal.ZERO) > 0);
        if (hasInvalidMember) {
            throw new BusinessException(TransferErrorCode.INVALID_SETTLED_AMOUNT);
        }
    }

    @Transactional
    public void delete(final Long userId, final Long transferId) {
        Transfer transfer = transferJpaRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE));

        if (!transfer.isCreatedBy(userId)) {
            throw new BusinessException(TransferErrorCode.DELETE_UNAUTHORIZED);
        }

        if (!transfer.isDeletableStatus()) {
            throw new BusinessException(TransferErrorCode.DELETE_FORBIDDEN_STATUS);
        }

        transferUserJpaRepository.deleteAllByTransferIdInBatch(transferId);
        transfer.markDeleted();
    }

    @Transactional
    public void check(final Long userId, final Long transferId) {
        Transfer transfer = transferJpaRepository.findByIdForUpdateWithGroupAndTravelItinerary(transferId).orElseThrow(() -> new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE));
        validateLeaderAuthorityOrThrow(userId, transfer);
        validateStatusOrThrow(transfer, TransferErrorCode.INVOICE_CHECK_NOT_ALLOWED_STATUS);
        transfer.confirm();
    }

    @Transactional
    public void completeMyTransfer(final Long userId, final Long transferId) {
        Transfer transfer = transferJpaRepository.findByIdForUpdateWithTransferUsers(transferId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.NOT_FOUND_INVOICE));

        if (transfer.getTransferStatus() != TransferStatus.CONFIRM) {
            throw new BusinessException(TransferErrorCode.INVOICE_DONE_NOT_ALLOWED_STATUS);
        }

        TransferUser transferUser = transfer.getTransferUsers().stream()
                .filter(tu -> tu.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(TransferErrorCode.TRANSFER_USER_NOT_FOUND));

        if (transferUser.isSettled()) {
            throw new BusinessException(TransferErrorCode.ALREADY_TRANSFERRED);
        }

        transferUser.settle();

        if (transfer.isAllPaid()) {
            transfer.markDone();
        }
    }
}
