package org.triple.backend.invoice.unit.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.invoice.dto.response.InvoiceDetailResponseDto;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.invoice.exception.InvoiceErrorCode;
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.invoice.service.InvoiceService;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ServiceTest
@Import(InvoiceService.class)
class InvoiceReadServiceTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceJpaRepository invoiceJpaRepository;

    @Autowired
    private InvoiceUserJpaRepository invoiceUserJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private TravelItineraryJpaRepository travelItineraryJpaRepository;

    @Autowired
    private UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Travel member can read invoice")
    void readByTravelItinerary_success() {
        User creator = saveUser("read-creator");
        User member1 = saveUser("read-member-1");
        User member2 = saveUser("read-member-2");
        Group group = saveGroup("read-group");
        TravelItinerary travel = saveTravelItinerary(group, "read-travel");
        saveTravelMembership(creator, travel, UserRole.LEADER);
        saveTravelMembership(member1, travel, UserRole.MEMBER);
        saveTravelMembership(member2, travel, UserRole.MEMBER);
        Invoice invoice = saveInvoice(creator, group, travel, InvoiceStatus.UNCONFIRM);
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, member1, new BigDecimal("7000")));
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, member2, new BigDecimal("3000")));
        entityManager.flush();
        entityManager.clear();

        InvoiceDetailResponseDto response = invoiceService.searchInvoice(member1.getId(), travel.getId());

        assertThat(response.title()).isEqualTo(invoice.getTitle());
        assertThat(response.creator().userId()).isEqualTo(creator.getId());
        assertThat(response.invoiceMembers()).hasSize(2);
        assertThat(response.remainingAmount()).isEqualByComparingTo("10000");
        assertThat(response.isDone()).isFalse();
    }

    @Test
    @DisplayName("isDone is true when remaining amount is zero")
    void readByTravelItinerary_doneWhenZero() {
        User creator = saveUser("done-creator");
        User member = saveUser("done-member");
        Group group = saveGroup("done-group");
        TravelItinerary travel = saveTravelItinerary(group, "done-travel");
        saveTravelMembership(creator, travel, UserRole.LEADER);
        saveTravelMembership(member, travel, UserRole.MEMBER);
        Invoice invoice = saveInvoice(creator, group, travel, InvoiceStatus.UNCONFIRM);
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, member, BigDecimal.ZERO));
        entityManager.flush();
        entityManager.clear();

        InvoiceDetailResponseDto response = invoiceService.searchInvoice(member.getId(), travel.getId());

        assertThat(response.remainingAmount()).isEqualByComparingTo("0");
        assertThat(response.isDone()).isTrue();
    }

    @Test
    @DisplayName("Non travel member cannot read invoice")
    void readByTravelItinerary_notTravelMember() {
        User creator = saveUser("forbidden-creator");
        User member = saveUser("forbidden-member");
        User outsider = saveUser("forbidden-outsider");
        Group group = saveGroup("forbidden-group");
        TravelItinerary travel = saveTravelItinerary(group, "forbidden-travel");
        saveTravelMembership(creator, travel, UserRole.LEADER);
        saveTravelMembership(member, travel, UserRole.MEMBER);
        Invoice invoice = saveInvoice(creator, group, travel, InvoiceStatus.UNCONFIRM);
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, member, new BigDecimal("10000")));
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> invoiceService.searchInvoice(outsider.getId(), travel.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(InvoiceErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND);
                });
    }

    private User saveUser(final String providerId) {
        return userJpaRepository.save(
                User.builder()
                        .providerId(providerId)
                        .nickname(providerId + "-nick")
                        .email(providerId + "@test.com")
                        .profileUrl("http://img")
                        .build()
        );
    }

    private Group saveGroup(final String groupName) {
        return groupJpaRepository.save(
                Group.create(
                        GroupKind.PUBLIC,
                        groupName,
                        "description",
                        "http://thumb",
                        10
                )
        );
    }

    private TravelItinerary saveTravelItinerary(final Group group, final String title) {
        return travelItineraryJpaRepository.save(
                new TravelItinerary(
                        title,
                        LocalDateTime.of(2030, 3, 20, 9, 0),
                        LocalDateTime.of(2030, 3, 22, 18, 0),
                        group,
                        "travel-description",
                        "http://travel-thumb",
                        5,
                        1,
                        false
                )
        );
    }

    private UserTravelItinerary saveTravelMembership(final User user, final TravelItinerary travelItinerary, final UserRole userRole) {
        return userTravelItineraryJpaRepository.save(UserTravelItinerary.of(user, travelItinerary, userRole));
    }

    private Invoice saveInvoice(
            final User creator,
            final Group group,
            final TravelItinerary travelItinerary,
            final InvoiceStatus invoiceStatus
    ) {
        return invoiceJpaRepository.save(
                Invoice.builder()
                        .group(group)
                        .creator(creator)
                        .travelItinerary(travelItinerary)
                        .invoiceStatus(invoiceStatus)
                        .title("invoice-title")
                        .description("invoice-description")
                        .totalAmount(new BigDecimal("10000"))
                        .dueAt(LocalDateTime.of(2030, 3, 31, 18, 0))
                        .build()
        );
    }
}
