package org.triple.backend.invoice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.invoice.dto.request.InvoiceCreateRequestDto;
import org.triple.backend.invoice.dto.response.InvoiceCreateResponseDto;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.invoice.exception.InvoiceErrorCode;
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.exception.TravelItineraryErrorCode;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

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

    private final InvoiceJpaRepository invoiceRepository;
    private final GroupJpaRepository groupJpaRepository;
    private final TravelItineraryJpaRepository travelItineraryJpaRepository;
    private final InvoiceUserJpaRepository invoiceUserJpaRepository;
    private final UserGroupJpaRepository userGroupJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Transactional
    public InvoiceCreateResponseDto create(final Long userId, final InvoiceCreateRequestDto dto) {
        validateTotalAmount(dto.recipients(), dto.totalAmount());

        Group group = groupJpaRepository.findById(dto.groupId()).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));
        TravelItinerary travelItinerary = travelItineraryJpaRepository.findByIdAndGroupIdAndIsDeletedFalseForUpdate(dto.travelItineraryId(), dto.groupId()).orElseThrow(() -> new BusinessException(TravelItineraryErrorCode.TRAVEL_NOT_FOUND));
        User owner = userGroupJpaRepository.findByGroupIdAndUserIdAndRoleAndJoinStatus(dto.groupId(), userId, Role.OWNER, JoinStatus.JOINED).orElseThrow(() -> new BusinessException(GroupErrorCode.NOT_GROUP_OWNER)).getUser();

        Map<Long, RecipientAmountDto> recipientByUserId = toRecipientMap(dto.recipients());
        Map<Long, User> userById = loadRecipientUsersOrThrow(dto.groupId(), recipientByUserId);

        Invoice savedInvoice = saveInvoiceOrThrow(dto, owner, travelItinerary, group);
        List<InvoiceUser> invoiceUsers = createInvoiceUsers(savedInvoice, recipientByUserId, userById);
        saveInvoiceUsersOrThrow(invoiceUsers);

        return InvoiceCreateResponseDto.from(savedInvoice, invoiceUsers);
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
        validateRecipientsInGroup(groupId, recipientUserIds);

        List<User> users = userJpaRepository.findAllById(recipientUserIds);
        if (users.size() != recipientUserIds.size()) {
            throw new BusinessException(InvoiceErrorCode.RECIPIENT_USER_NOT_FOUND);
        }

        return users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private Invoice saveInvoiceOrThrow(
            final InvoiceCreateRequestDto dto,
            final User owner,
            final TravelItinerary travelItinerary,
            final Group group
    ) {
        try {
            return invoiceRepository.saveAndFlush(
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

    private void validateRecipientsInGroup(final Long groupId, final List<Long> recipientUserIds) {
        long count = userGroupJpaRepository.countJoinedUsersInGroup(groupId, JoinStatus.JOINED, recipientUserIds);
        if(count != recipientUserIds.size()) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }
    }
}
