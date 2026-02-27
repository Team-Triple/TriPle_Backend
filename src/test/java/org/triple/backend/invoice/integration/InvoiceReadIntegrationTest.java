package org.triple.backend.invoice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.triple.backend.common.DbCleaner;
import org.triple.backend.common.annotation.IntegrationTest;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class InvoiceReadIntegrationTest {

    private static final String USER_SESSION_KEY = "USER_ID";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DbCleaner dbCleaner;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private TravelItineraryJpaRepository travelItineraryJpaRepository;

    @Autowired
    private UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;

    @Autowired
    private InvoiceJpaRepository invoiceJpaRepository;

    @Autowired
    private InvoiceUserJpaRepository invoiceUserJpaRepository;

    @BeforeEach
    void setUp() {
        dbCleaner.clean();
    }

    @Test
    @DisplayName("Travel member can read invoice by travel itinerary")
    void readInvoiceByTravelItinerary_success() throws Exception {
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

        mockMvc.perform(get("/invoices/travels/{travelItineraryId}", travel.getId())
                        .sessionAttr(USER_SESSION_KEY, member1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("invoice-title"))
                .andExpect(jsonPath("$.creator.userId").value(creator.getId()))
                .andExpect(jsonPath("$.invoiceMembers.length()").value(2))
                .andExpect(jsonPath("$.remainingAmount").value(10000))
                .andExpect(jsonPath("$.isDone").value(false));
    }

    @Test
    @DisplayName("Non travel member cannot read invoice")
    void readInvoiceByTravelItinerary_notTravelMember() throws Exception {
        User creator = saveUser("forbidden-creator");
        User member = saveUser("forbidden-member");
        User outsider = saveUser("forbidden-outsider");
        Group group = saveGroup("forbidden-group");
        TravelItinerary travel = saveTravelItinerary(group, "forbidden-travel");
        saveTravelMembership(creator, travel, UserRole.LEADER);
        saveTravelMembership(member, travel, UserRole.MEMBER);
        Invoice invoice = saveInvoice(creator, group, travel, InvoiceStatus.UNCONFIRM);
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, member, new BigDecimal("10000")));

        mockMvc.perform(get("/invoices/travels/{travelItineraryId}", travel.getId())
                        .sessionAttr(USER_SESSION_KEY, outsider.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Unauthenticated user gets 401")
    void readInvoiceByTravelItinerary_unauthorized() throws Exception {
        mockMvc.perform(get("/invoices/travels/{travelItineraryId}", 1L))
                .andExpect(status().isUnauthorized());
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

    private Group saveGroup(final String name) {
        return groupJpaRepository.save(
                Group.create(
                        GroupKind.PUBLIC,
                        name,
                        "description",
                        "http://thumb",
                        10
                )
        );
    }

    private UserTravelItinerary saveTravelMembership(final User user, final TravelItinerary travelItinerary, final UserRole userRole) {
        return userTravelItineraryJpaRepository.save(UserTravelItinerary.of(user, travelItinerary, userRole));
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
