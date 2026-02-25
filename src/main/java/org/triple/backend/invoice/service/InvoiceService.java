package org.triple.backend.invoice.service;

import lombok.RequiredArgsConstructor;
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
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.triple.backend.invoice.dto.request.InvoiceCreateRequestDto.*;

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

        Long groupId = dto.groupId();
        Long travelItineraryId = dto.travelItineraryId();
        List<RecipientAmountDto> recipients = dto.recipients();
        String title = dto.title();
        String description = dto.description();
        BigDecimal totalAmount = dto.totalAmount();
        LocalDateTime dueAt = dto.dueAt();

        BigDecimal sum = recipients.stream().map(RecipientAmountDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if(sum.compareTo(totalAmount) != 0) {
            throw new BusinessException(InvoiceErrorCode.INVALID_TOTAL_AMOUNT);
        }

        // 그룹에 락을 걸 필요가 있을까?
        Group group = groupJpaRepository.findById(groupId).orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        TravelItinerary travelItinerary = travelItineraryJpaRepository.findByIdAndGroupIdAndIsDeletedFalseForUpdate(travelItineraryId, groupId).orElseThrow(() -> new BusinessException(TravelItineraryErrorCode.TRAVEL_NOT_FOUND));

        // 해당 그룹 오너 여부 파악
        if(!userGroupJpaRepository.existsByGroupIdAndUserIdAndRoleAndJoinStatus(groupId, userId, Role.OWNER, JoinStatus.JOINED)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_OWNER);
        }

        User owner = userJpaRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        Map<Long, RecipientAmountDto> recipientByUserId = recipients.stream()
                .collect(Collectors.toMap(
                        RecipientAmountDto::userId,
                        r -> r,
                        (a,b) -> {throw new BusinessException(InvoiceErrorCode.DUPLICATE_RECIPIENT);},
                        LinkedHashMap::new
                ));

        List<Long> recipientUserIds = new ArrayList<>(recipientByUserId.keySet());
        long count = userGroupJpaRepository.countJoinedUsersInGroup(groupId, JoinStatus.JOINED, recipientUserIds);
        if(count != recipientUserIds.size()) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        // 청구서 생성
        List<User> users = userJpaRepository.findAllById(recipientUserIds);
        if(users.size() != recipientUserIds.size()) {
            throw new BusinessException(InvoiceErrorCode.RECIPIENT_USER_NOT_FOUND);
        }

        Map<Long, User> userById = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        Invoice savedInvoice = invoiceRepository.save(Invoice.create(title, description, totalAmount, dueAt, owner, travelItinerary, group));

        List<InvoiceUser> invoiceUsers = recipientByUserId.values().stream()
                .map(r -> InvoiceUser.create(savedInvoice, userById.get(r.userId()), r.amount()))
                .toList();

        invoiceUserJpaRepository.saveAll(invoiceUsers);

        return InvoiceCreateResponseDto.from(savedInvoice, invoiceUsers);
    }
}
