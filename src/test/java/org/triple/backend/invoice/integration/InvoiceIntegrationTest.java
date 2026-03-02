package org.triple.backend.invoice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.triple.backend.common.DbCleaner;
import org.triple.backend.common.annotation.IntegrationTest;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.auth.session.CsrfTokenManager.CSRF_HEADER;
import static org.triple.backend.auth.session.CsrfTokenManager.CSRF_TOKEN_KEY;

@IntegrationTest
class InvoiceIntegrationTest {

    private static final String USER_SESSION_KEY = "USER_ID";
    private static final String CSRF_TOKEN = "test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DbCleaner dbCleaner;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private UserGroupJpaRepository userGroupJpaRepository;

    @Autowired
    private TravelItineraryJpaRepository travelItineraryJpaRepository;

    @Autowired
    private UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;

    @Autowired
    private InvoiceJpaRepository invoiceJpaRepository;

    @Autowired
    private InvoiceUserJpaRepository invoiceUserJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dbCleaner.clean();
    }

    @Test
    @DisplayName("여행장(LEADER)이 청구서 생성 API를 호출하면 청구서와 청구 대상이 저장된다.")
    void 여행장_LEADER가_청구서_생성_API를_호출하면_청구서와_청구_대상이_저장된다() throws Exception {
        // given
        User leader = saveUser("leader");
        User member = saveUser("member");
        Group group = saveGroup("정산 그룹");
        saveMembership(leader, group, Role.OWNER);
        saveMembership(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "제주 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        String body = """
                {
                  "groupId": %d,
                  "travelItineraryId": %d,
                  "recipients": [
                    { "userId": %d, "amount": 30000 }
                  ],
                  "title": "제주 렌트비 정산",
                  "description": "렌트비 N빵",
                  "totalAmount": 30000,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """.formatted(group.getId(), travelItinerary.getId(), member.getId());

        // when & then
        mockMvc.perform(post("/invoices")
                        .sessionAttr(USER_SESSION_KEY, leader.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").isNumber())
                .andExpect(jsonPath("$.groupId").value(group.getId()))
                .andExpect(jsonPath("$.travelItineraryId").value(travelItinerary.getId()))
                .andExpect(jsonPath("$.title").value("제주 렌트비 정산"))
                .andExpect(jsonPath("$.recipients.length()").value(1))
                .andExpect(jsonPath("$.recipients[0].userId").value(member.getId()))
                .andExpect(jsonPath("$.recipients[0].amount").value(30000));

        assertThat(invoiceJpaRepository.count()).isEqualTo(1L);
        assertThat(invoiceUserJpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("그룹 OWNER가 아니어도 여행장(LEADER)이면 청구서를 생성할 수 있다.")
    void 그룹_OWNER가_아니어도_여행장_LEADER이면_청구서를_생성할_수_있다() throws Exception {
        // given
        User owner = saveUser("owner");
        User leaderMember = saveUser("leader-member");
        User member = saveUser("member");
        Group group = saveGroup("정산 그룹");
        saveMembership(owner, group, Role.OWNER);
        saveMembership(leaderMember, group, Role.MEMBER);
        saveMembership(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "제주 여행");
        saveTravelMembership(leaderMember, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        String body = """
                {
                  "groupId": %d,
                  "travelItineraryId": %d,
                  "recipients": [
                    { "userId": %d, "amount": 30000 }
                  ],
                  "title": "제주 렌트비 정산",
                  "description": "렌트비 N빵",
                  "totalAmount": 30000,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """.formatted(group.getId(), travelItinerary.getId(), member.getId());

        // when & then
        mockMvc.perform(post("/invoices")
                        .sessionAttr(USER_SESSION_KEY, leaderMember.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").isNumber())
                .andExpect(jsonPath("$.groupId").value(group.getId()))
                .andExpect(jsonPath("$.travelItineraryId").value(travelItinerary.getId()));

        assertThat(invoiceJpaRepository.count()).isEqualTo(1L);
        assertThat(invoiceUserJpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("비로그인 사용자가 청구서 생성을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_생성을_요청하면_401을_반환한다() throws Exception {
        String body = """
                {
                  "groupId": 1,
                  "travelItineraryId": 1,
                  "recipients": [
                    { "userId": 2, "amount": 30000 }
                  ],
                  "title": "제주 렌트비 정산",
                  "description": "렌트비 N빵",
                  "totalAmount": 30000,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """;

        mockMvc.perform(post("/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 청구서 생성 요청 시 403을 반환한다.")
    void 여행장_LEADER가_아니면_청구서_생성_요청_시_403을_반환한다() throws Exception {
        // given
        User leader = saveUser("leader");
        User member = saveUser("member");
        Group group = saveGroup("정산 그룹");
        saveMembership(leader, group, Role.OWNER);
        saveMembership(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "제주 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        String body = """
                {
                  "groupId": %d,
                  "travelItineraryId": %d,
                  "recipients": [
                    { "userId": %d, "amount": 30000 }
                  ],
                  "title": "제주 렌트비 정산",
                  "description": "렌트비 N빵",
                  "totalAmount": 30000,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """.formatted(group.getId(), travelItinerary.getId(), member.getId());

        // when & then
        mockMvc.perform(post("/invoices")
                        .sessionAttr(USER_SESSION_KEY, member.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("같은 여행 일정으로 청구서를 중복 생성하면 409를 반환한다.")
    void 같은_여행_일정으로_청구서를_중복_생성하면_409를_반환한다() throws Exception {
        // given
        User leader = saveUser("leader");
        User member = saveUser("member");
        Group group = saveGroup("정산 그룹");
        saveMembership(leader, group, Role.OWNER);
        saveMembership(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "제주 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        String body = """
                {
                  "groupId": %d,
                  "travelItineraryId": %d,
                  "recipients": [
                    { "userId": %d, "amount": 30000 }
                  ],
                  "title": "제주 렌트비 정산",
                  "description": "렌트비 N빵",
                  "totalAmount": 30000,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """.formatted(group.getId(), travelItinerary.getId(), member.getId());

        mockMvc.perform(post("/invoices")
                        .sessionAttr(USER_SESSION_KEY, leader.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // when & then
        mockMvc.perform(post("/invoices")
                        .sessionAttr(USER_SESSION_KEY, leader.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("중복된 청구서 생성입니다."));

        assertThat(invoiceJpaRepository.count()).isEqualTo(1L);
        assertThat(invoiceUserJpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("여행장(LEADER)은 청구서 메타 정보를 수정할 수 있다.")
    void 여행장_LEADER은_청구서_메타_정보를_수정할_수_있다() throws Exception {
        // given
        User leader = saveUser("leader-update");
        Group group = saveGroup("수정 그룹");
        saveMembership(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "수정 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Invoice invoice = saveInvoice(group, leader, travelItinerary, InvoiceStatus.UNCONFIRM, "기존 제목");

        String body = """
                {
                  "title": "수정된 제목",
                  "description": "수정된 설명",
                  "dueAt": "2030-04-01T18:00:00"
                }
                """;

        // when & then
        mockMvc.perform(patch("/invoices/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, leader.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoice.getId()))
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.description").value("수정된 설명"))
                .andExpect(jsonPath("$.dueAt").value("2030-04-01T18:00:00"))
                .andExpect(jsonPath("$.invoiceStatus").value("UNCONFIRM"));

        Invoice updatedInvoice = invoiceJpaRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updatedInvoice.getTitle()).isEqualTo("수정된 제목");
        assertThat(updatedInvoice.getDescription()).isEqualTo("수정된 설명");
        assertThat(updatedInvoice.getDueAt()).isEqualTo(LocalDateTime.of(2030, 4, 1, 18, 0));
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 청구서 메타 정보 수정 요청 시 403을 반환한다.")
    void 여행장_LEADER가_아니면_청구서_메타_정보_수정_요청_시_403을_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-update-fail");
        User member = saveUser("member-update-fail");
        Group group = saveGroup("리더 검증 그룹");
        saveMembership(leader, group, Role.OWNER);
        saveMembership(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "리더 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);
        Invoice invoice = saveInvoice(group, leader, travelItinerary, InvoiceStatus.UNCONFIRM, "기존 제목");

        String body = """
                {
                  "title": "수정 시도",
                  "description": "수정 시도 설명",
                  "dueAt": "2030-04-02T18:00:00"
                }
                """;

        // when & then
        mockMvc.perform(patch("/invoices/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("여행장 권한이 필요합니다."));
    }

    @Test
    @DisplayName("비로그인 사용자가 청구서 메타 정보 수정을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_메타_정보_수정을_요청하면_401을_반환한다() throws Exception {
        String body = """
                {
                  "title": "수정된 제목",
                  "description": "수정된 설명",
                  "dueAt": "2030-04-01T18:00:00"
                }
                """;

        mockMvc.perform(patch("/invoices/{invoiceId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("여행 멤버는 여행 일정 기준으로 청구서를 조회할 수 있다.")
    void 여행_멤버는_여행_일정_기준으로_청구서를_조회할_수_있다() throws Exception {
        User 생성자 = saveUser("read-creator");
        User 멤버1 = saveUser("read-member-1");
        User 멤버2 = saveUser("read-member-2");
        Group 그룹 = saveGroup("조회 그룹");
        TravelItinerary 여행일정 = saveTravelItinerary(그룹, "조회 여행");
        saveTravelMembership(생성자, 여행일정, UserRole.LEADER);
        saveTravelMembership(멤버1, 여행일정, UserRole.MEMBER);
        saveTravelMembership(멤버2, 여행일정, UserRole.MEMBER);
        Invoice 청구서 = saveInvoice(생성자, 그룹, 여행일정, InvoiceStatus.UNCONFIRM);
        invoiceUserJpaRepository.save(InvoiceUser.create(청구서, 멤버1, new BigDecimal("7000")));
        invoiceUserJpaRepository.save(InvoiceUser.create(청구서, 멤버2, new BigDecimal("3000")));

        mockMvc.perform(get("/invoices/travels/{travelItineraryId}", 여행일정.getId())
                        .sessionAttr(USER_SESSION_KEY, 멤버1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("청구서"))
                .andExpect(jsonPath("$.creator.userId").value(생성자.getId()))
                .andExpect(jsonPath("$.invoiceMembers.length()").value(2))
                .andExpect(jsonPath("$.remainingAmount").value(10000))
                .andExpect(jsonPath("$.isDone").value(false));
    }

    @Test
    @DisplayName("여행 일정 멤버가 아니면 청구서 조회 시 404를 반환한다.")
    void 여행_일정_멤버가_아니면_청구서_조회_시_404를_반환한다() throws Exception {
        User 생성자 = saveUser("forbidden-creator");
        User 멤버 = saveUser("forbidden-member");
        User 외부인 = saveUser("forbidden-outsider");
        Group 그룹 = saveGroup("권한 그룹");
        TravelItinerary 여행일정 = saveTravelItinerary(그룹, "권한 여행");
        saveTravelMembership(생성자, 여행일정, UserRole.LEADER);
        saveTravelMembership(멤버, 여행일정, UserRole.MEMBER);
        Invoice 청구서 = saveInvoice(생성자, 그룹, 여행일정, InvoiceStatus.UNCONFIRM);
        invoiceUserJpaRepository.save(InvoiceUser.create(청구서, 멤버, new BigDecimal("10000")));

        mockMvc.perform(get("/invoices/travels/{travelItineraryId}", 여행일정.getId())
                        .sessionAttr(USER_SESSION_KEY, 외부인.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("비로그인 사용자가 청구서 조회를 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_조회를_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/invoices/travels/{travelItineraryId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("여행장(LEADER)은 청구서 금액/대상 정보를 수정할 수 있다.")
    void 여행장_LEADER은_청구서_금액_대상_정보를_수정할_수_있다() throws Exception {
        // given
        User leader = saveUser("leader-adjust");
        User member1 = saveUser("member-adjust-1");
        User member2 = saveUser("member-adjust-2");
        Group group = saveGroup("정산 수정 그룹");
        saveMembership(leader, group, Role.OWNER);
        saveMembership(member1, group, Role.MEMBER);
        saveMembership(member2, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "정산 수정 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member1, travelItinerary, UserRole.MEMBER);
        saveTravelMembership(member2, travelItinerary, UserRole.MEMBER);

        Invoice invoice = saveInvoice(group, leader, travelItinerary, InvoiceStatus.UNCONFIRM, "기존 제목");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, member1, new java.math.BigDecimal("70000")));

        String body = """
                {
                  "totalAmount": 30000,
                  "recipients": [
                    { "userId": %d, "amount": 10000 },
                    { "userId": %d, "amount": 20000 }
                  ]
                }
                """.formatted(member1.getId(), member2.getId());

        // when & then
        mockMvc.perform(put("/invoices/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, leader.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoice.getId()))
                .andExpect(jsonPath("$.totalAmount").value(30000))
                .andExpect(jsonPath("$.recipients.length()").value(2))
                .andExpect(jsonPath("$.invoiceStatus").value("UNCONFIRM"));

        Invoice updatedInvoice = invoiceJpaRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updatedInvoice.getTotalAmount()).isEqualByComparingTo("30000");
        assertThat(invoiceUserJpaRepository.findAll().stream()
                .filter(iu -> iu.getInvoice().getId().equals(invoice.getId()))
                .toList())
                .hasSize(2);
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 청구서 금액/대상 정보 수정 요청 시 403을 반환한다.")
    void 여행장_LEADER가_아니면_청구서_금액_대상_정보_수정_요청_시_403을_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-adjust-forbidden");
        User member = saveUser("member-adjust-forbidden");
        Group group = saveGroup("정산 수정 권한 그룹");
        saveMembership(leader, group, Role.OWNER);
        saveMembership(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "정산 수정 권한 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        Invoice invoice = saveInvoice(group, leader, travelItinerary, InvoiceStatus.UNCONFIRM, "기존 제목");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, member, new java.math.BigDecimal("10000")));

        String body = """
                {
                  "totalAmount": 10000,
                  "recipients": [
                    { "userId": %d, "amount": 10000 }
                  ]
                }
                """.formatted(member.getId());

        // when & then
        mockMvc.perform(put("/invoices/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("결제 내역이 있는 청구서는 금액/대상 정보 수정 요청 시 409를 반환한다.")
    void 결제_내역이_있는_청구서는_금액_대상_정보_수정_요청_시_409를_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-adjust-payment-409");
        User member = saveUser("member-adjust-payment-409");
        Group group = saveGroup("정산 결제 검증 그룹");
        saveMembership(leader, group, Role.OWNER);
        saveMembership(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "정산 결제 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        Invoice invoice = saveInvoice(group, leader, travelItinerary, InvoiceStatus.UNCONFIRM, "기존 제목");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, member, new java.math.BigDecimal("10000")));
        jdbcTemplate.update("insert into payment (invoice_id, payment_status) values (?, ?)", invoice.getId(), "READY");

        String body = """
                {
                  "totalAmount": 10000,
                  "recipients": [
                    { "userId": %d, "amount": 10000 }
                  ]
                }
                """.formatted(member.getId());

        // when & then
        mockMvc.perform(put("/invoices/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, leader.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("결제 내역이 있는 청구서는 수정할 수 없습니다."));
    }

    @Test
    @DisplayName("여행장(LEADER)은 청구서를 확인(CONFIRM)할 수 있다.")
    void 여행장_LEADER은_청구서를_확인할_수_있다() throws Exception {
        // given
        User leader = saveUser("leader-check");
        Group group = saveGroup("확인 그룹");
        saveMembership(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확인 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Invoice invoice = saveInvoice(group, leader, travelItinerary, InvoiceStatus.UNCONFIRM, "확인 대상 청구서");

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, leader.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        Invoice confirmedInvoice = invoiceJpaRepository.findById(invoice.getId()).orElseThrow();
        assertThat(confirmedInvoice.getInvoiceStatus()).isEqualTo(InvoiceStatus.CONFIRM);
    }

    @Test
    @DisplayName("비로그인 사용자가 청구서 확인을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_확인을_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/invoices/{invoiceId}/check", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("그룹 멤버가 아니면 청구서 확인 요청 시 403을 반환한다.")
    void 그룹_멤버가_아니면_청구서_확인_요청_시_403을_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-check-403-group");
        User outsider = saveUser("outsider-check-403-group");
        Group group = saveGroup("확인 권한 그룹");
        saveMembership(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확인 권한 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Invoice invoice = saveInvoice(group, leader, travelItinerary, InvoiceStatus.UNCONFIRM, "확인 대상 청구서");

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, outsider.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹을 조회할 권한이 없습니다."));
    }

    @Test
    @DisplayName("여행 멤버십이 없으면 청구서 확인 요청 시 404를 반환한다.")
    void 여행_멤버십이_없으면_청구서_확인_요청_시_404를_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-check-404-travel");
        User groupMemberWithoutTravel = saveUser("group-member-without-travel");
        Group group = saveGroup("확인 여행 멤버십 그룹");
        saveMembership(leader, group, Role.OWNER);
        saveMembership(groupMemberWithoutTravel, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확인 여행 멤버십 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Invoice invoice = saveInvoice(group, leader, travelItinerary, InvoiceStatus.UNCONFIRM, "확인 대상 청구서");

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, groupMemberWithoutTravel.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("여행 내 해당 유저를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 청구서 확인 요청 시 403을 반환한다.")
    void 여행장_LEADER가_아니면_청구서_확인_요청_시_403을_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-check-403-leader");
        User member = saveUser("member-check-403-leader");
        Group group = saveGroup("확인 리더 권한 그룹");
        saveMembership(leader, group, Role.OWNER);
        saveMembership(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확인 리더 권한 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);
        Invoice invoice = saveInvoice(group, leader, travelItinerary, InvoiceStatus.UNCONFIRM, "확인 대상 청구서");

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("여행장 권한이 필요합니다."));
    }

    @Test
    @DisplayName("존재하지 않는 청구서 확인 요청 시 404를 반환한다.")
    void 존재하지_않는_청구서_확인_요청_시_404를_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-check-404-invoice");

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", 999L)
                        .sessionAttr(USER_SESSION_KEY, leader.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 청구서 입니다."));
    }

    @Test
    @DisplayName("확인할 수 없는 상태의 청구서 확인 요청 시 409를 반환한다.")
    void 확인할_수_없는_상태의_청구서_확인_요청_시_409를_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-check-409-status");
        Group group = saveGroup("확인 상태 검증 그룹");
        saveMembership(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확인 상태 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Invoice invoice = saveInvoice(group, leader, travelItinerary, InvoiceStatus.CONFIRM, "이미 확정된 청구서");

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, leader.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("청구서를 확인할 수 없습니다."));
    }

    @Test
    @DisplayName("결제 내역이 있는 청구서 확인 요청 시 409를 반환한다.")
    void 결제_내역이_있는_청구서_확인_요청_시_409를_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-check-409-payment");
        Group group = saveGroup("확인 결제 검증 그룹");
        saveMembership(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확인 결제 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Invoice invoice = saveInvoice(group, leader, travelItinerary, InvoiceStatus.UNCONFIRM, "확인 대상 청구서");
        jdbcTemplate.update("insert into payment (invoice_id, payment_status) values (?, ?)", invoice.getId(), "READY");

        // when & then
        mockMvc.perform(post("/invoices/{invoiceId}/check", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, leader.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("결제 내역이 있는 청구서는 확인할 수 없습니다."));
    }

    @Test
    @DisplayName("청구서 생성자가 삭제 요청하면 상태가 DELETED로 변경되고 invoice_user가 삭제된다.")
    void 청구서_삭제_성공() throws Exception {
        User creator = saveUser("delete-creator");
        User member = saveUser("delete-member");
        Group group = saveGroup("삭제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "삭제 여행");
        Invoice invoice = saveInvoice(creator, group, travelItinerary, InvoiceStatus.UNCONFIRM);
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, member, new java.math.BigDecimal("10000")));

        mockMvc.perform(delete("/invoices/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, creator.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        Invoice deletedInvoice = invoiceJpaRepository.findById(invoice.getId()).orElseThrow();
        assertThat(deletedInvoice.getInvoiceStatus()).isEqualTo(InvoiceStatus.DELETED);
        assertThat(invoiceUserJpaRepository.findAll().stream()
                .filter(invoiceUser -> invoiceUser.getInvoice().getId().equals(invoice.getId()))
                .toList()).isEmpty();
    }

    @Test
    @DisplayName("청구서 생성자가 아니면 삭제 요청 시 403을 반환한다.")
    void 청구서_생성자가_아니면_삭제_요청_시_403을_반환한다() throws Exception {
        User creator = saveUser("delete-creator-403");
        User other = saveUser("delete-other-403");
        Group group = saveGroup("삭제 권한 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "삭제 권한 여행");
        Invoice invoice = saveInvoice(creator, group, travelItinerary, InvoiceStatus.UNCONFIRM);

        mockMvc.perform(delete("/invoices/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, other.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("청구서 상태가 UNCONFIRM이 아니면 삭제 요청 시 409를 반환한다.")
    void 청구서_상태가_UNCONFIRM이_아니면_삭제_요청_시_409를_반환한다() throws Exception {
        User creator = saveUser("delete-status-409");
        Group group = saveGroup("삭제 상태 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "삭제 상태 여행");
        Invoice invoice = saveInvoice(creator, group, travelItinerary, InvoiceStatus.CONFIRM);

        mockMvc.perform(delete("/invoices/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, creator.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("결제 내역이 있으면 삭제 요청 시 409를 반환한다.")
    void 결제_내역이_있으면_삭제_요청_시_409를_반환한다() throws Exception {
        User creator = saveUser("delete-payment-409");
        Group group = saveGroup("삭제 결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "삭제 결제 여행");
        Invoice invoice = saveInvoice(creator, group, travelItinerary, InvoiceStatus.UNCONFIRM);

        jdbcTemplate.update("insert into payment (invoice_id, payment_status) values (?, ?)", invoice.getId(), "READY");

        mockMvc.perform(delete("/invoices/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, creator.getId())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isConflict());
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
                        "설명",
                        "http://thumb",
                        10
                )
        );
    }

    private UserGroup saveMembership(final User user, final Group group, final Role role) {
        return userGroupJpaRepository.save(UserGroup.create(user, group, role));
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
                        "여행 설명",
                        "http://travel-thumb",
                        5,
                        1,
                        false
                )
        );
    }

    private Invoice saveInvoice(
            final Group group,
            final User creator,
            final TravelItinerary travelItinerary,
            final InvoiceStatus invoiceStatus,
            final String title
    ) {
        return invoiceJpaRepository.save(
                Invoice.builder()
                        .group(group)
                        .creator(creator)
                        .travelItinerary(travelItinerary)
                        .invoiceStatus(invoiceStatus)
                        .title(title)
                        .description("기존 설명")
                        .totalAmount(new java.math.BigDecimal("70000"))
                        .dueAt(LocalDateTime.of(2030, 3, 31, 18, 0))
                        .build()
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
                        .title("청구서")
                        .description("청구서 설명")
                        .totalAmount(new java.math.BigDecimal("10000"))
                        .dueAt(LocalDateTime.of(2030, 3, 31, 18, 0))
                        .build()
        );
    }
}
