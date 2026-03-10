package org.triple.backend.payment.integration;

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
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.invoice.entity.Invoice;
import org.triple.backend.invoice.entity.InvoiceStatus;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.invoice.exception.InvoiceErrorCode;
import org.triple.backend.invoice.repository.InvoiceJpaRepository;
import org.triple.backend.invoice.repository.InvoiceUserJpaRepository;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentMethod;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.entity.PgProvider;
import org.triple.backend.payment.repository.PaymentJpaRepository;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.auth.session.CsrfTokenManager.CSRF_HEADER;
import static org.triple.backend.auth.session.CsrfTokenManager.CSRF_TOKEN_KEY;

@IntegrationTest
class PaymentIntegrationTest {

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
    private TravelItineraryJpaRepository travelItineraryJpaRepository;

    @Autowired
    private UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;

    @Autowired
    private InvoiceJpaRepository invoiceJpaRepository;

    @Autowired
    private InvoiceUserJpaRepository invoiceUserJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @BeforeEach
    void setUp() {
        dbCleaner.clean();
    }

    @Test
    @DisplayName("결제 대상 사용자가 결제 생성 API를 호출하면 결제 정보가 저장된다.")
    void 결제_대상_사용자가_결제_생성_API를_호출하면_결제_정보가_저장된다() throws Exception {
        // given
        User payer = saveUser("payer-success");
        Group group = saveGroup("결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "결제 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "제주 렌트비");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, new BigDecimal("10000")));

        String body = """
                {
                  "amount": 3000,
                  "name": "제주 렌트비"
                }
                """;

        // when & then
        mockMvc.perform(post("/payments/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, payer.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").isString())
                .andExpect(jsonPath("$.orderName").value("제주 렌트비"))
                .andExpect(jsonPath("$.amount").value(3000));

        assertThat(paymentJpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("비로그인 사용자가 결제 생성 API를 호출하면 401을 반환한다.")
    void 비로그인_사용자가_결제_생성_API를_호출하면_401을_반환한다() throws Exception {
        String body = """
                {
                  "amount": 3000,
                  "name": "제주 렌트비"
                }
                """;

        mockMvc.perform(post("/payments/{invoiceId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."));
    }

    @Test
    @DisplayName("CSRF 토큰이 유효하지 않으면 결제 생성 API는 403을 반환한다.")
    void CSRF_토큰이_유효하지_않으면_결제_생성_API는_403을_반환한다() throws Exception {
        // given
        User payer = saveUser("payer-invalid-csrf");
        Group group = saveGroup("결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "결제 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "제주 렌트비");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, new BigDecimal("10000")));

        String body = """
                {
                  "amount": 3000,
                  "name": "제주 렌트비"
                }
                """;

        // when & then
        mockMvc.perform(post("/payments/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, payer.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF 토큰이 유효하지 않습니다."));
    }

    @Test
    @DisplayName("결제 생성 요청 바디가 유효하지 않으면 400을 반환한다.")
    void 결제_생성_요청_바디가_유효하지_않으면_400을_반환한다() throws Exception {
        // given
        User payer = saveUser("payer-bad-request");
        Group group = saveGroup("결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "결제 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "제주 렌트비");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, new BigDecimal("10000")));

        String body = """
                {
                  "amount": 0,
                  "name": " "
                }
                """;

        // when & then
        mockMvc.perform(post("/payments/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, payer.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("결제 진행 불가능한 청구서로 요청하면 403을 반환한다.")
    void 결제_진행_불가능한_청구서로_요청하면_403을_반환한다() throws Exception {
        // given
        User payer = saveUser("payer-not-allowed");
        Group group = saveGroup("결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "결제 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.UNCONFIRM, "미확정 청구서");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, new BigDecimal("10000")));

        String body = """
                {
                  "amount": 3000,
                  "name": "미확정 결제"
                }
                """;

        // when & then
        mockMvc.perform(post("/payments/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, payer.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("결제를 진행할 수 없는 청구서입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 청구서로 결제 생성 요청하면 404를 반환한다.")
    void 존재하지_않는_청구서로_결제_생성_요청하면_404를_반환한다() throws Exception {
        User payer = saveUser("payer-not-found-invoice");

        String body = """
                {
                  "amount": 3000,
                  "name": "존재하지 않는 청구서 결제"
                }
                """;

        mockMvc.perform(post("/payments/{invoiceId}", 99999L)
                        .sessionAttr(USER_SESSION_KEY, payer.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 청구서 입니다."));
    }

    @Test
    @DisplayName("남은 금액이 0이면 결제 생성 요청 시 409를 반환한다.")
    void 남은_금액이_0이면_결제_생성_요청_시_409를_반환한다() throws Exception {
        // given
        User payer = saveUser("payer-completed");
        Group group = saveGroup("완납 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "완납 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "완납 청구서");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, BigDecimal.ZERO));

        String body = """
                {
                  "amount": 1000,
                  "name": "완납 결제 시도"
                }
                """;

        // when & then
        mockMvc.perform(post("/payments/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, payer.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 결제가 완료된 청구 대상입니다."));
    }

    @Test
    @DisplayName("요청 결제 금액이 남은 금액을 초과하면 409를 반환한다.")
    void 요청_결제_금액이_남은_금액을_초과하면_409를_반환한다() throws Exception {
        // given
        User payer = saveUser("payer-exceeds");
        Group group = saveGroup("초과 결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "초과 결제 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "초과 결제 청구서");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, new BigDecimal("2000")));

        String body = """
                {
                  "amount": 3000,
                  "name": "초과 결제 시도"
                }
                """;

        // when & then
        mockMvc.perform(post("/payments/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, payer.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("요청 결제 금액이 남은 금액을 초과합니다."));
    }

    @Test
    @DisplayName("이미 진행중인 결제가 있으면 결제 생성 요청 시 403을 반환한다.")
    void 이미_진행중인_결제가_있으면_결제_생성_요청_시_403을_반환한다() throws Exception {
        // given
        User payer = saveUser("payer-active");
        Group group = saveGroup("진행중 결제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "진행중 결제 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "진행중 결제 청구서");
        invoiceUserJpaRepository.save(InvoiceUser.create(invoice, payer, new BigDecimal("10000")));
        paymentJpaRepository.save(
                Payment.builder()
                        .invoice(invoice)
                        .user(payer)
                        .pgProvider(PgProvider.TOSS)
                        .method(PaymentMethod.TRANSFER)
                        .orderId(UUID.randomUUID().toString())
                        .requestedAmount(new BigDecimal("1000"))
                        .paymentStatus(PaymentStatus.READY)
                        .requestedAt(LocalDateTime.now())
                        .build()
        );

        String body = """
                {
                  "amount": 2000,
                  "name": "중복 결제 시도"
                }
                """;

        // when & then
        mockMvc.perform(post("/payments/{invoiceId}", invoice.getId())
                        .sessionAttr(USER_SESSION_KEY, payer.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이미 실행중인 결제입니다."));
    }

    @Test
    @DisplayName("로그인한 사용자는 결제 목록을 조회할 수 있다.")
    void 로그인한_사용자는_결제_목록을_조회할_수_있다() throws Exception {
        User payer = saveUser("payer-search-success");
        User other = saveUser("payer-search-other");
        Group group = saveGroup("결제 조회 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "결제 조회 여행");
        TravelItinerary otherTravelItinerary = saveTravelItinerary(group, "타인 결제 조회 여행");
        Invoice invoice = saveInvoice(group, payer, travelItinerary, InvoiceStatus.CONFIRM, "제주 렌트비");
        Invoice otherInvoice = saveInvoice(group, other, otherTravelItinerary, InvoiceStatus.CONFIRM, "타인 결제 청구서");
        paymentJpaRepository.save(
                Payment.builder()
                        .invoice(invoice)
                        .user(payer)
                        .pgProvider(PgProvider.TOSS)
                        .method(PaymentMethod.TRANSFER)
                        .orderId(UUID.randomUUID().toString())
                        .requestedAmount(new BigDecimal("3000"))
                        .paymentStatus(PaymentStatus.READY)
                        .requestedAt(LocalDateTime.of(2030, 3, 20, 12, 0))
                        .build()
        );
        paymentJpaRepository.save(
                Payment.builder()
                        .invoice(otherInvoice)
                        .user(other)
                        .pgProvider(PgProvider.TOSS)
                        .method(PaymentMethod.TRANSFER)
                        .orderId(UUID.randomUUID().toString())
                        .requestedAmount(new BigDecimal("7000"))
                        .paymentStatus(PaymentStatus.READY)
                        .requestedAt(LocalDateTime.of(2030, 3, 20, 13, 0))
                        .build()
        );

        mockMvc.perform(get("/payments")
                        .sessionAttr(USER_SESSION_KEY, payer.getPublicUuid())
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].invoiceId").value(invoice.getId()))
                .andExpect(jsonPath("$.items[0].name").value("제주 렌트비"))
                .andExpect(jsonPath("$.nextCursor").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("비로그인 사용자가 결제 목록 조회를 요청하면 401을 반환한다.")
    void 비로그인_사용자가_결제_목록_조회를_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/payments")
                        .param("size", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."));
    }

    @Test
    @DisplayName("검색어 길이가 20자를 초과하면 결제 목록 조회 시 400을 반환한다.")
    void 검색어_길이가_20자를_초과하면_결제_목록_조회_시_400을_반환한다() throws Exception {
        User payer = saveUser("payer-search-invalid");

        mockMvc.perform(get("/payments")
                        .sessionAttr(USER_SESSION_KEY, payer.getPublicUuid())
                        .param("keyword", "aaaaaaaaaaaaaaaaaaaaa")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("검색어는 최대 20자까지 입력할 수 있습니다."));
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

    private UserTravelItinerary saveTravelMembership(
            final User user,
            final TravelItinerary travelItinerary,
            final UserRole userRole
    ) {
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
                        .description("청구서 설명")
                        .totalAmount(new BigDecimal("10000"))
                        .dueAt(LocalDateTime.of(2030, 3, 31, 18, 0))
                        .build()
        );
    }
}
