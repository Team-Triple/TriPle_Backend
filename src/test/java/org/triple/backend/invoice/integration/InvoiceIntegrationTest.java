package org.triple.backend.invoice.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.message").value("여행장만 청구서를 생성할 수 있습니다."));
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
}
