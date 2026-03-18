package org.triple.backend.transfer.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.triple.backend.auth.session.UuidCrypto;
import org.triple.backend.common.DbCleaner;
import org.triple.backend.common.annotation.IntegrationTest;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.transfer.entity.Transfer;
import org.triple.backend.transfer.entity.TransferStatus;
import org.triple.backend.transfer.entity.TransferUser;
import org.triple.backend.transfer.repository.TransferJpaRepository;
import org.triple.backend.transfer.repository.TransferUserJpaRepository;
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
class TransferIntegrationTest {

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
    private TransferJpaRepository transferJpaRepository;

    @Autowired
    private TransferUserJpaRepository transferUserJpaRepository;

    @Autowired
    private UuidCrypto uuidCrypto;

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

        String body = createBody(group.getId(), travelItinerary.getId(), encryptedUserId(member), 30000, 30000);

        // when & then
        mockMvc.perform(post("/transfers")
                        .sessionAttr(USER_SESSION_KEY, leader.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").isNumber())
                .andExpect(jsonPath("$.accountNumber").value("999999-00-999999"))
                .andExpect(jsonPath("$.bankName").value("KB국민"))
                .andExpect(jsonPath("$.accountHolder").value("김민준"))
                .andExpect(jsonPath("$.totalAmount").value(30000))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].id").isString())
                .andExpect(jsonPath("$.members[0].amount").value(30000));

        assertThat(transferJpaRepository.count()).isEqualTo(1L);
        assertThat(transferUserJpaRepository.count()).isEqualTo(1L);
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

        String body = createBody(group.getId(), travelItinerary.getId(), encryptedUserId(member), 30000, 30000);

        // when & then
        mockMvc.perform(post("/transfers")
                        .sessionAttr(USER_SESSION_KEY, leaderMember.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").isNumber())
                .andExpect(jsonPath("$.accountNumber").value("999999-00-999999"))
                .andExpect(jsonPath("$.members.length()").value(1));

        assertThat(transferJpaRepository.count()).isEqualTo(1L);
        assertThat(transferUserJpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("비로그인 사용자가 청구서 생성을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_생성을_요청하면_401을_반환한다() throws Exception {
        String body = createBody(1L, 1L, "enc-sample-user-id", 30000, 30000);

        mockMvc.perform(post("/transfers")
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

        String body = createBody(group.getId(), travelItinerary.getId(), encryptedUserId(member), 30000, 30000);

        // when & then
        mockMvc.perform(post("/transfers")
                        .sessionAttr(USER_SESSION_KEY, member.getPublicUuid())
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

        String body = createBody(group.getId(), travelItinerary.getId(), encryptedUserId(member), 30000, 30000);

        mockMvc.perform(post("/transfers")
                        .sessionAttr(USER_SESSION_KEY, leader.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // when & then
        mockMvc.perform(post("/transfers")
                        .sessionAttr(USER_SESSION_KEY, leader.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("중복된 청구서 생성입니다."));

        assertThat(transferJpaRepository.count()).isEqualTo(1L);
        assertThat(transferUserJpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("정산 완료 멤버 금액이 양수인 정산서 생성 요청 시 400을 반환한다.")
    void 정산_완료_멤버_금액이_양수인_청구서_생성_요청_시_400을_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-invalid-settled-create");
        User member = saveUser("member-invalid-settled-create");
        Group group = saveGroup("정산 검증 그룹");
        saveMembership(leader, group, Role.OWNER);
        saveMembership(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "정산 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        String body = createBody(
                group.getId(),
                travelItinerary.getId(),
                encryptedUserId(member),
                30000,
                30000,
                true
        );

        // when & then
        mockMvc.perform(post("/transfers")
                        .sessionAttr(USER_SESSION_KEY, leader.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("정산 완료 멤버의 금액은 0이어야 합니다."));
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
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 제목");

        String body = """
                {
                  "title": "수정된 제목",
                  "description": "수정된 설명",
                  "dueAt": "2030-04-01T18:00:00"
                }
                """;

        // when & then
        mockMvc.perform(patch("/transfers/{transferId}", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, leader.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(transfer.getId()))
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.description").value("수정된 설명"))
                .andExpect(jsonPath("$.dueAt").value("2030-04-01T18:00:00"))
                .andExpect(jsonPath("$.transferStatus").value("UNCONFIRM"));

        Transfer updatedTransfer = transferJpaRepository.findById(transfer.getId()).orElseThrow();
        assertThat(updatedTransfer.getTitle()).isEqualTo("수정된 제목");
        assertThat(updatedTransfer.getDescription()).isEqualTo("수정된 설명");
        assertThat(updatedTransfer.getDueAt()).isEqualTo(LocalDateTime.of(2030, 4, 1, 18, 0));
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
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 제목");

        String body = """
                {
                  "title": "수정 시도",
                  "description": "수정 시도 설명",
                  "dueAt": "2030-04-02T18:00:00"
                }
                """;

        // when & then
        mockMvc.perform(patch("/transfers/{transferId}", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getPublicUuid())
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

        mockMvc.perform(patch("/transfers/{transferId}", 1L)
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
        Transfer 청구서 = saveTransfer(생성자, 그룹, 여행일정, TransferStatus.UNCONFIRM);
        transferUserJpaRepository.save(TransferUser.create(청구서, 멤버1, new BigDecimal("7000")));
        transferUserJpaRepository.save(TransferUser.create(청구서, 멤버2, new BigDecimal("3000")));

        mockMvc.perform(get("/transfers/travels/{travelItineraryId}", 여행일정.getId())
                        .sessionAttr(USER_SESSION_KEY, 멤버1.getPublicUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("999999-00-999999"))
                .andExpect(jsonPath("$.bankName").value("KB국민"))
                .andExpect(jsonPath("$.accountHolder").value("김민준"))
                .andExpect(jsonPath("$.members.length()").value(2))
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
        Transfer 청구서 = saveTransfer(생성자, 그룹, 여행일정, TransferStatus.UNCONFIRM);
        transferUserJpaRepository.save(TransferUser.create(청구서, 멤버, new BigDecimal("10000")));

        mockMvc.perform(get("/transfers/travels/{travelItineraryId}", 여행일정.getId())
                        .sessionAttr(USER_SESSION_KEY, 외부인.getPublicUuid()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("비로그인 사용자가 청구서 조회를 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_조회를_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/transfers/travels/{travelItineraryId}", 1L))
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

        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 제목");
        transferUserJpaRepository.save(TransferUser.create(transfer, member1, new java.math.BigDecimal("70000")));

        String body = updateInfoBody(encryptedUserId(member1), encryptedUserId(member2), 10000, 20000, 30000);

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, leader.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(transfer.getId()))
                .andExpect(jsonPath("$.accountNumber").value("999999-00-999999"))
                .andExpect(jsonPath("$.bankName").value("KB국민"))
                .andExpect(jsonPath("$.accountHolder").value("김민준"))
                .andExpect(jsonPath("$.totalAmount").value(30000))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.transferStatus").value("UNCONFIRM"));

        Transfer updatedTransfer = transferJpaRepository.findById(transfer.getId()).orElseThrow();
        assertThat(updatedTransfer.getTotalAmount()).isEqualByComparingTo("30000");
        assertThat(transferUserJpaRepository.findAll().stream()
                .filter(iu -> iu.getTransfer().getId().equals(transfer.getId()))
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

        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 제목");
        transferUserJpaRepository.save(TransferUser.create(transfer, member, new java.math.BigDecimal("10000")));

        String body = updateInfoBody(encryptedUserId(member), encryptedUserId(member), 10000, 0, 10000);

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("정산 완료 멤버 금액이 양수인 정산서 금액/대상 정보 수정 요청 시 400을 반환한다.")
    void 정산_완료_멤버_금액이_양수인_청구서_금액_대상_정보_수정_요청_시_400을_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-adjust-invalid-settled");
        User member1 = saveUser("member-adjust-invalid-settled-1");
        User member2 = saveUser("member-adjust-invalid-settled-2");
        Group group = saveGroup("정산 수정 검증 그룹");
        saveMembership(leader, group, Role.OWNER);
        saveMembership(member1, group, Role.MEMBER);
        saveMembership(member2, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "정산 수정 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member1, travelItinerary, UserRole.MEMBER);
        saveTravelMembership(member2, travelItinerary, UserRole.MEMBER);

        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 제목");
        transferUserJpaRepository.save(TransferUser.create(transfer, member1, new java.math.BigDecimal("70000")));

        String body = updateInfoBody(
                encryptedUserId(member1),
                encryptedUserId(member2),
                10000,
                20000,
                30000,
                true,
                false
        );

        // when & then
        mockMvc.perform(put("/transfers/{transferId}", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, leader.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("정산 완료 멤버의 금액은 0이어야 합니다."));
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
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "확인 대상 청구서");

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, leader.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        Transfer confirmedTransfer = transferJpaRepository.findById(transfer.getId()).orElseThrow();
        assertThat(confirmedTransfer.getTransferStatus()).isEqualTo(TransferStatus.CONFIRM);
    }

    @Test
    @DisplayName("비로그인 사용자가 청구서 확인을 요청하면 401을 반환한다.")
    void 비로그인_사용자가_청구서_확인을_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/transfers/{transferId}/check", 1L))
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
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "확인 대상 청구서");

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, outsider.getPublicUuid())
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
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "확인 대상 청구서");

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, groupMemberWithoutTravel.getPublicUuid())
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
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "확인 대상 청구서");

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, member.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("여행장 권한이 필요합니다."));
    }

    @Test
    @DisplayName("존재하지 않는 청구서 확인 요청 시 404를 반환한다.")
    void 존재하지_않는_청구서_확인_요청_시_404를_반환한다() throws Exception {
        // given
        User leader = saveUser("leader-check-404-transfer");

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", 999L)
                        .sessionAttr(USER_SESSION_KEY, leader.getPublicUuid())
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
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.CONFIRM, "이미 확정된 청구서");

        // when & then
        mockMvc.perform(post("/transfers/{transferId}/check", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, leader.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("청구서를 확인할 수 없습니다."));
    }

    @Test
    @DisplayName("청구서 생성자가 삭제 요청하면 상태가 DELETED로 변경되고 transfer_user가 삭제된다.")
    void 청구서_삭제_성공() throws Exception {
        User creator = saveUser("delete-creator");
        User member = saveUser("delete-member");
        Group group = saveGroup("삭제 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "삭제 여행");
        Transfer transfer = saveTransfer(creator, group, travelItinerary, TransferStatus.UNCONFIRM);
        transferUserJpaRepository.save(TransferUser.create(transfer, member, new java.math.BigDecimal("10000")));

        mockMvc.perform(delete("/transfers/{transferId}", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, creator.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        Transfer deletedTransfer = transferJpaRepository.findById(transfer.getId()).orElseThrow();
        assertThat(deletedTransfer.getTransferStatus()).isEqualTo(TransferStatus.DELETED);
        assertThat(transferUserJpaRepository.findAll().stream()
                .filter(transferUser -> transferUser.getTransfer().getId().equals(transfer.getId()))
                .toList()).isEmpty();
    }

    @Test
    @DisplayName("청구서 생성자가 아니면 삭제 요청 시 403을 반환한다.")
    void 청구서_생성자가_아니면_삭제_요청_시_403을_반환한다() throws Exception {
        User creator = saveUser("delete-creator-403");
        User other = saveUser("delete-other-403");
        Group group = saveGroup("삭제 권한 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "삭제 권한 여행");
        Transfer transfer = saveTransfer(creator, group, travelItinerary, TransferStatus.UNCONFIRM);

        mockMvc.perform(delete("/transfers/{transferId}", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, other.getPublicUuid())
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
        Transfer transfer = saveTransfer(creator, group, travelItinerary, TransferStatus.CONFIRM);

        mockMvc.perform(delete("/transfers/{transferId}", transfer.getId())
                        .sessionAttr(USER_SESSION_KEY, creator.getPublicUuid())
                        .sessionAttr(CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isConflict());
    }

    private User saveUser(final String providerId) {
        return userJpaRepository.saveAndFlush(
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
                        1,
                        false
                )
        );
    }

    private Transfer saveTransfer(
            final Group group,
            final User creator,
            final TravelItinerary travelItinerary,
            final TransferStatus transferStatus,
            final String title
    ) {
        return transferJpaRepository.save(
                Transfer.builder()
                        .group(group)
                        .creator(creator)
                        .travelItinerary(travelItinerary)
                        .transferStatus(transferStatus)
                        .title(title)
                        .description("기존 설명")
                        .accountNumber("999999-00-999999")
                        .bankName("KB국민")
                        .accountHolder("김민준")
                        .totalAmount(new java.math.BigDecimal("70000"))
                        .dueAt(LocalDateTime.of(2030, 3, 31, 18, 0))
                        .build()
        );
    }

    private Transfer saveTransfer(
            final User creator,
            final Group group,
            final TravelItinerary travelItinerary,
            final TransferStatus transferStatus
    ) {
        return transferJpaRepository.save(
                Transfer.builder()
                        .group(group)
                        .creator(creator)
                        .travelItinerary(travelItinerary)
                        .transferStatus(transferStatus)
                        .title("청구서")
                        .description("청구서 설명")
                        .accountNumber("999999-00-999999")
                        .bankName("KB국민")
                        .accountHolder("김민준")
                        .totalAmount(new java.math.BigDecimal("10000"))
                        .dueAt(LocalDateTime.of(2030, 3, 31, 18, 0))
                        .build()
        );
    }

    private String encryptedUserId(final User user) {
        return uuidCrypto.encrypt(user.getPublicUuid());
    }

    private String createBody(
            final Long groupId,
            final Long travelItineraryId,
            final String memberId,
            final int memberAmount,
            final int totalAmount
    ) {
        return createBody(groupId, travelItineraryId, memberId, memberAmount, totalAmount, false);
    }

    private String createBody(
            final Long groupId,
            final Long travelItineraryId,
            final String memberId,
            final int memberAmount,
            final int totalAmount,
            final boolean settled
    ) {
        return """
                {
                  "accountNumber": "999999-00-999999",
                  "bankName": "KB국민",
                  "accountHolder": "김민준",
                  "groupId": %d,
                  "travelItineraryId": %d,
                  "members": [
                    { "id": "%s", "name": "멤버", "avatar": "http://img", "amount": %d, "settled": false }
                  ],
                  "title": "제주 렌트비 정산",
                  "description": "렌트비 N빵",
                  "totalAmount": %d,
                  "dueAt": "2030-03-31T18:00:00"
                }
                """.formatted(groupId, travelItineraryId, memberId, memberAmount, totalAmount);
    }

    private String updateInfoBody(
            final String firstMemberId,
            final String secondMemberId,
            final int firstAmount,
            final int secondAmount,
            final int totalAmount
    ) {
        return updateInfoBody(
                firstMemberId,
                secondMemberId,
                firstAmount,
                secondAmount,
                totalAmount,
                false,
                secondAmount == 0
        );
    }

    private String updateInfoBody(
            final String firstMemberId,
            final String secondMemberId,
            final int firstAmount,
            final int secondAmount,
            final int totalAmount,
            final boolean firstSettled,
            final boolean secondSettled
    ) {
        return """
                {
                  "accountNumber": "999999-00-999999",
                  "bankName": "KB국민",
                  "accountHolder": "김민준",
                  "totalAmount": %d,
                  "members": [
                    { "id": "%s", "name": "멤버1", "avatar": "http://img", "amount": %d, "settled": false },
                    { "id": "%s", "name": "멤버2", "avatar": "http://img", "amount": %d, "settled": %s }
                  ]
                }
                """.formatted(
                totalAmount,
                firstMemberId,
                firstAmount,
                firstSettled,
                secondMemberId,
                secondAmount,
                secondSettled
        );
    }
}
