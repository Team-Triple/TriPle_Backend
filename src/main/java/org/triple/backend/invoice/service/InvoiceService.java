package org.triple.backend.invoice.service;

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
import org.triple.backend.invoice.dto.request.InvoiceCreateRequestDto;
import org.triple.backend.invoice.dto.request.InvoiceUpdateRequestDto;
import org.triple.backend.invoice.dto.response.InvoiceCreateResponseDto;
import org.triple.backend.invoice.dto.response.InvoiceUpdateResponseDto;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.invoice.exception.InvoiceErrorCode;
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.payment.repository.PaymentJpaRepository;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.exception.TravelItineraryErrorCode;
import org.triple.backend.travel.exception.UserTravelItineraryErrorCode;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.triple.backend.invoice.dto.request.InvoiceCreateRequestDto.RecipientAmountDto;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceJpaRepository invoiceJpaRepository;
    private final GroupJpaRepository groupJpaRepository;
    private final TravelItineraryJpaRepository travelItineraryJpaRepository;
    private final UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;
    private final InvoiceUserJpaRepository invoiceUserJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final UserGroupJpaRepository userGroupJpaRepository;

    @Transactional
    public InvoiceCreateResponseDto create(final Long userId, final InvoiceCreateRequestDto dto) {
        validateTotalAmount(dto.recipients(), dto.totalAmount());

        Group group = groupJpaRepository.findById(dto.groupId()).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        TravelItinerary travelItinerary = travelItineraryJpaRepository.findByIdAndGroupIdAndIsDeletedFalseForUpdate(dto.travelItineraryId(), dto.groupId()).orElseThrow(() -> new BusinessException(TravelItineraryErrorCode.TRAVEL_NOT_FOUND));

        UserTravelItinerary creatorTravelMembership = userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(userId, travelItinerary.getId())
                .orElseThrow(() -> new BusinessException(InvoiceErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND));

        if (!creatorTravelMembership.getUserRole().equals(UserRole.LEADER)) {
            throw new BusinessException(InvoiceErrorCode.NOT_TRAVEL_LEADER);
        }

        User creator = creatorTravelMembership.getUser();

        Map<Long, RecipientAmountDto> recipientByUserId = toRecipientMap(dto.recipients());
        Map<Long, User> userById = loadRecipientUsersOrThrow(dto.groupId(), recipientByUserId);

        Invoice savedInvoice = saveInvoiceOrThrow(dto, creator, travelItinerary, group);
        List<InvoiceUser> invoiceUsers = createInvoiceUsers(savedInvoice, recipientByUserId, userById);
        saveInvoiceUsersOrThrow(invoiceUsers);

        return InvoiceCreateResponseDto.from(savedInvoice, invoiceUsers);
    }

    @Transactional
    public InvoiceUpdateResponseDto updateMetaInfo(final Long userId, final Long invoiceId, final InvoiceUpdateRequestDto dto) {

        Invoice invoice = invoiceJpaRepository.findByIdForUpdateWithGroupAndTravelItinerary(invoiceId).orElseThrow(() -> new BusinessException(InvoiceErrorCode.NOT_FOUND_INVOICE));

        if(!invoice.getInvoiceStatus().equals(InvoiceStatus.UNCONFIRM)) {
            throw new BusinessException(InvoiceErrorCode.INVOICE_UPDATE_NOT_ALLOWED_STATUS);
        }

        if(!userGroupJpaRepository.existsByGroupIdAndUserIdAndJoinStatus(invoice.getGroup().getId(), userId, JoinStatus.JOINED)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        UserTravelItinerary userTravelItinerary = userTravelItineraryJpaRepository.findByUserIdAndTravelItineraryId(userId, invoice.getTravelItinerary().getId())
                .orElseThrow(() -> new BusinessException(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND));

        if(!userTravelItinerary.getUserRole().equals(UserRole.LEADER)) {
            throw new BusinessException(InvoiceErrorCode.NOT_TRAVEL_LEADER);
        }

        invoice.update(dto.title(), dto.description(), dto.dueAt());

        try {
            invoiceJpaRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(InvoiceErrorCode.CONCURRENT_INVOICE_UPDATE);
        }

        return InvoiceUpdateResponseDto.from(invoice);
    }

    private Map<Long, RecipientAmountDto> toRecipientMap(final List<RecipientAmountDto> recipients) {
        return recipients.stream()
                .collect(Collectors.toMap(
                        RecipientAmountDto::userId,
                        r -> r,
                        (a,b) -> {throw new BusinessException(InvoiceErrorCode.DUPLICATE_RECIPIENT);},
                        LinkedHashMap::new
                ));
    }

    private Map<Long, User> loadRecipientUsersOrThrow(
            final Long groupId,
            final Map<Long, RecipientAmountDto> recipientByUserId
    ) {
        List<Long> recipientUserIds = new ArrayList<>(recipientByUserId.keySet());
        List<UserGroup> lockedUserGroups = userGroupJpaRepository.findJoinedUsersInGroupForUpdate(groupId, JoinStatus.JOINED, recipientUserIds);
        if (lockedUserGroups.size() != recipientUserIds.size()) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        return lockedUserGroups.stream().map(UserGroup::getUser).collect(Collectors.toMap(User::getId, user -> user));
    }

    private Invoice saveInvoiceOrThrow(
            final InvoiceCreateRequestDto dto,
            final User owner,
            final TravelItinerary travelItinerary,
            final Group group
    ) {
        try {
            return invoiceJpaRepository.saveAndFlush(
                    Invoice.create(
                            dto.title(),
                            dto.description(),
                            dto.totalAmount(),
                            dto.dueAt(),
                            owner,
                            travelItinerary,
                            group
                    )
            );
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(InvoiceErrorCode.DUPLICATE_INVOICE);
        }
    }

    private List<InvoiceUser> createInvoiceUsers(
            final Invoice savedInvoice,
            final Map<Long, RecipientAmountDto> recipientByUserId,
            final Map<Long, User> userById
    ) {
        return recipientByUserId.values().stream()
                .map(recipient -> InvoiceUser.create(savedInvoice, userById.get(recipient.userId()), recipient.amount()))
                .toList();
    }

    private void saveInvoiceUsersOrThrow(final List<InvoiceUser> invoiceUsers) {
        try {
            invoiceUserJpaRepository.saveAll(invoiceUsers);
            invoiceUserJpaRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(InvoiceErrorCode.DUPLICATE_RECIPIENT);
        }
    }

    private void validateTotalAmount(final List<RecipientAmountDto> recipients, final BigDecimal totalAmount) {
        BigDecimal sum = recipients.stream().map(RecipientAmountDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if(sum.compareTo(totalAmount) != 0) {
            throw new BusinessException(InvoiceErrorCode.INVALID_TOTAL_AMOUNT);
        }
    }

    @Transactional
    public void delete(final Long userId, final Long invoiceId) {
        Invoice invoice = invoiceJpaRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new BusinessException(InvoiceErrorCode.NOT_FOUND_INVOICE));

        if (!invoice.isCreatedBy(userId)) {
            throw new BusinessException(InvoiceErrorCode.DELETE_UNAUTHORIZED);
        }

        if (!invoice.isDeletableStatus()) {
            throw new BusinessException(InvoiceErrorCode.DELETE_FORBIDDEN_STATUS);
        }

        if (paymentJpaRepository.existsByInvoiceId(invoiceId)) {
            throw new BusinessException(InvoiceErrorCode.DELETE_FORBIDDEN_PAYMENT_EXISTS);
        }

        invoiceUserJpaRepository.deleteAllByInvoiceIdInBatch(invoiceId);
        invoice.markDeleted();
    }
}
