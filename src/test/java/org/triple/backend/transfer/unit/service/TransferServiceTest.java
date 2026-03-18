package org.triple.backend.transfer.unit.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.transfer.dto.RecipientAmountDto;
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
import org.triple.backend.transfer.service.TransferService;
import org.triple.backend.travel.entity.TravelItinerary;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.entity.UserTravelItinerary;
import org.triple.backend.travel.exception.UserTravelItineraryErrorCode;
import org.triple.backend.travel.repository.TravelItineraryJpaRepository;
import org.triple.backend.travel.repository.UserTravelItineraryJpaRepository;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;
import org.triple.backend.user.service.UserFinder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ServiceTest
@Import({TransferService.class, UserFinder.class})
class TransferServiceTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferJpaRepository transferRepository;

    @Autowired
    private TransferUserJpaRepository transferUserJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private UserGroupJpaRepository userGroupJpaRepository;

    @Autowired
    private TravelItineraryJpaRepository travelItineraryJpaRepository;

    @Autowired
    private UserTravelItineraryJpaRepository userTravelItineraryJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("여행장(LEADER)은 청구서를 생성할 수 있다.")
    void 여행장_LEADER은_청구서를_생성할_수_있다() {
        // given
        User leader = saveUser("leader");
        User member1 = saveUser("member-1");
        User member2 = saveUser("member-2");
        Group group = saveGroup("정산 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(member1, group, Role.MEMBER);
        saveUserGroup(member2, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "제주 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member1, travelItinerary, UserRole.MEMBER);
        saveTravelMembership(member2, travelItinerary, UserRole.MEMBER);

        TransferCreateRequestDto request = createRequest(
                group.getId(),
                travelItinerary.getId(),
                List.of(
                        new RecipientAmountDto(member1.getPublicUuid().toString(), new BigDecimal("30000")),
                        new RecipientAmountDto(member2.getPublicUuid().toString(), new BigDecimal("40000"))
                ),
                "제주 렌트비 정산",
                "렌트비 N빵",
                new BigDecimal("70000"),
                LocalDateTime.of(2030, 3, 31, 18, 0)
        );

        // when
        TransferCreateResponseDto response = transferService.create(leader.getId(), request);

        // then
        assertThat(response.transferId()).isNotNull();
        assertThat(response.accountNumber()).isEqualTo("999999-00-999999");
        assertThat(response.bankName()).isEqualTo("KB국민");
        assertThat(response.accountHolder()).isEqualTo("김민준");
        assertThat(response.totalAmount()).isEqualByComparingTo("70000");
        assertThat(response.members()).hasSize(2);

        assertThat(transferRepository.count()).isEqualTo(1L);
        assertThat(transferUserJpaRepository.count()).isEqualTo(2L);

        Transfer savedTransfer = transferRepository.findById(response.transferId()).orElseThrow();
        assertThat(savedTransfer.getCreator().getId()).isEqualTo(leader.getId());
        assertThat(savedTransfer.getGroup().getId()).isEqualTo(group.getId());
        assertThat(savedTransfer.getTravelItinerary().getId()).isEqualTo(travelItinerary.getId());
        assertThat(savedTransfer.getTitle()).isEqualTo("제주 렌트비 정산");

        List<TransferUser> transferUsers = transferUserJpaRepository.findAll();
        assertThat(transferUsers).extracting(iu -> iu.getUser().getId())
                .containsExactlyInAnyOrder(member1.getId(), member2.getId());
    }

    @Test
    @DisplayName("그룹 OWNER가 아니어도 여행장(LEADER)이면 청구서를 생성할 수 있다.")
    void 그룹_OWNER가_아니어도_여행장_LEADER이면_청구서를_생성할_수_있다() {
        // given
        User owner = saveUser("owner");
        User leaderMember = saveUser("leader-member");
        User member = saveUser("member");
        Group group = saveGroup("정산 그룹");
        saveUserGroup(owner, group, Role.OWNER);
        saveUserGroup(leaderMember, group, Role.MEMBER);
        saveUserGroup(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "제주 여행");
        saveTravelMembership(leaderMember, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        TransferCreateRequestDto request = createRequest(
                group.getId(),
                travelItinerary.getId(),
                List.of(new RecipientAmountDto(member.getPublicUuid().toString(), new BigDecimal("30000"))),
                "제주 렌트비 정산",
                "렌트비 N빵",
                new BigDecimal("30000"),
                LocalDateTime.of(2030, 3, 31, 18, 0)
        );

        // when
        TransferCreateResponseDto response = transferService.create(leaderMember.getId(), request);

        // then
        assertThat(response.transferId()).isNotNull();
        Transfer savedTransfer = transferRepository.findById(response.transferId()).orElseThrow();
        assertThat(savedTransfer.getCreator().getId()).isEqualTo(leaderMember.getId());
        assertThat(transferRepository.count()).isEqualTo(1L);
        assertThat(transferUserJpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("총 금액과 대상 금액 합계가 다르면 INVALID_TOTAL_AMOUNT 예외가 발생한다.")
    void 총_금액과_대상_금액_합계가_다르면_INVALID_TOTAL_AMOUNT_예외가_발생한다() {
        // given
        TransferCreateRequestDto request = createRequest(
                1L,
                1L,
                List.of(
                        new RecipientAmountDto("00000000-0000-0000-0000-000000000002", new BigDecimal("30000"))
                ),
                "제주 렌트비 정산",
                "렌트비 N빵",
                new BigDecimal("40000"),
                LocalDateTime.of(2030, 3, 31, 18, 0)
        );

        // when & then
        assertThatThrownBy(() -> transferService.create(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.INVALID_TOTAL_AMOUNT);
                });
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 NOT_TRAVEL_LEADER 예외가 발생한다.")
    void 여행장_LEADER가_아니면_NOT_TRAVEL_LEADER_예외가_발생한다() {
        // given
        User leader = saveUser("leader");
        User member = saveUser("member");
        Group group = saveGroup("정산 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "제주 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        TransferCreateRequestDto request = createRequest(
                group.getId(),
                travelItinerary.getId(),
                List.of(new RecipientAmountDto(member.getPublicUuid().toString(), new BigDecimal("30000"))),
                "제주 렌트비 정산",
                "렌트비 N빵",
                new BigDecimal("30000"),
                LocalDateTime.of(2030, 3, 31, 18, 0)
        );

        // when & then
        assertThatThrownBy(() -> transferService.create(member.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.NOT_TRAVEL_LEADER);
                });
    }

    @Test
    @DisplayName("정산 완료 멤버의 금액이 0보다 크면 INVALID_SETTLED_AMOUNT 예외가 발생한다.")
    void 정산_완료_멤버_금액이_양수면_INVALID_SETTLED_AMOUNT_예외가_발생한다() {
        // given
        User leader = saveUser("leader-settled");
        User member = saveUser("member-settled");
        Group group = saveGroup("정산 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "제주 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        TransferCreateRequestDto request = new TransferCreateRequestDto(
                "999999-00-999999",
                "KB국민",
                "김민준",
                new BigDecimal("30000"),
                List.of(
                        new TransferCreateRequestDto.MemberDto(
                                member.getPublicUuid().toString(),
                                "멤버",
                                "http://img",
                                new BigDecimal("30000"),
                                true
                        )
                ),
                group.getId(),
                travelItinerary.getId(),
                "제주 렌트비 정산",
                "렌트비 N빵",
                LocalDateTime.of(2030, 3, 31, 18, 0)
        );

        // when & then
        assertThatThrownBy(() -> transferService.create(leader.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.INVALID_SETTLED_AMOUNT);
                });
    }

    @Test
    @DisplayName("청구 대상 사용자에 중복이 있으면 DUPLICATE_RECIPIENT 예외가 발생한다.")
    void 청구_대상_사용자에_중복이_있으면_DUPLICATE_RECIPIENT_예외가_발생한다() {
        // given
        User leader = saveUser("leader");
        User member = saveUser("member");
        Group group = saveGroup("정산 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "제주 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        TransferCreateRequestDto request = createRequest(
                group.getId(),
                travelItinerary.getId(),
                List.of(
                        new RecipientAmountDto(member.getPublicUuid().toString(), new BigDecimal("10000")),
                        new RecipientAmountDto(member.getPublicUuid().toString(), new BigDecimal("20000"))
                ),
                "제주 렌트비 정산",
                "렌트비 N빵",
                new BigDecimal("30000"),
                LocalDateTime.of(2030, 3, 31, 18, 0)
        );

        // when & then
        assertThatThrownBy(() -> transferService.create(leader.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.DUPLICATE_RECIPIENT);
                });
    }

    @Test
    @DisplayName("청구 대상이 그룹 멤버가 아니면 NOT_GROUP_MEMBER 예외가 발생한다.")
    void 청구_대상이_그룹_멤버가_아니면_NOT_GROUP_MEMBER_예외가_발생한다() {
        // given
        User leader = saveUser("leader");
        User outsider = saveUser("outsider");
        Group group = saveGroup("정산 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "제주 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);

        TransferCreateRequestDto request = createRequest(
                group.getId(),
                travelItinerary.getId(),
                List.of(new RecipientAmountDto(outsider.getPublicUuid().toString(), new BigDecimal("30000"))),
                "제주 렌트비 정산",
                "렌트비 N빵",
                new BigDecimal("30000"),
                LocalDateTime.of(2030, 3, 31, 18, 0)
        );

        // when & then
        assertThatThrownBy(() -> transferService.create(leader.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.NOT_GROUP_MEMBER);
                });
    }

    @Test
    @DisplayName("같은 여행 일정으로 중복 청구서를 생성하면 DUPLICATE_INVOICE 예외가 발생한다.")
    void 같은_여행_일정으로_중복_청구서를_생성하면_DUPLICATE_INVOICE_예외가_발생한다() {
        // given
        User leader = saveUser("leader");
        User member = saveUser("member");
        Group group = saveGroup("정산 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "제주 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        TransferCreateRequestDto firstRequest = createRequest(
                group.getId(),
                travelItinerary.getId(),
                List.of(new RecipientAmountDto(member.getPublicUuid().toString(), new BigDecimal("30000"))),
                "제주 렌트비 정산",
                "렌트비 N빵",
                new BigDecimal("30000"),
                LocalDateTime.of(2030, 3, 31, 18, 0)
        );
        TransferCreateRequestDto secondRequest = createRequest(
                group.getId(),
                travelItinerary.getId(),
                List.of(new RecipientAmountDto(member.getPublicUuid().toString(), new BigDecimal("30000"))),
                "중복 청구서",
                "중복 생성 시도",
                new BigDecimal("30000"),
                LocalDateTime.of(2030, 4, 1, 18, 0)
        );

        transferService.create(leader.getId(), firstRequest);

        // when & then
        assertThatThrownBy(() -> transferService.create(leader.getId(), secondRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.DUPLICATE_INVOICE);
                });
    }

    @Test
    @DisplayName("여행 멤버는 청구서를 조회할 수 있다.")
    void 여행_멤버는_청구서를_조회할_수_있다() {
        // given
        User creator = saveUser("read-creator");
        User member1 = saveUser("read-member-1");
        User member2 = saveUser("read-member-2");
        Group group = saveGroup("조회 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "조회 여행");
        saveTravelMembership(creator, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member1, travelItinerary, UserRole.MEMBER);
        saveTravelMembership(member2, travelItinerary, UserRole.MEMBER);
        Transfer transfer = saveTransfer(creator, group, travelItinerary, TransferStatus.UNCONFIRM);
        transferUserJpaRepository.save(TransferUser.create(transfer, member1, new BigDecimal("7000")));
        transferUserJpaRepository.save(TransferUser.create(transfer, member2, new BigDecimal("3000")));
        entityManager.flush();
        entityManager.clear();

        // when
        TransferDetailResponseDto response = transferService.searchTransfer(member1.getId(), travelItinerary.getId());

        // then
        assertThat(response.accountNumber()).isNotBlank();
        assertThat(response.members()).hasSize(2);
        assertThat(response.remainingAmount()).isEqualByComparingTo("10000");
        assertThat(response.isDone()).isFalse();
    }

    @Test
    @DisplayName("남은 금액이 0이면 청구서 완료 상태를 참으로 반환한다.")
    void 남은_금액이_0이면_청구서_완료_상태를_참으로_반환한다() {
        // given
        User creator = saveUser("done-creator");
        User member = saveUser("done-member");
        Group group = saveGroup("완료 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "완료 여행");
        saveTravelMembership(creator, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);
        Transfer transfer = saveTransfer(creator, group, travelItinerary, TransferStatus.UNCONFIRM);
        transferUserJpaRepository.save(TransferUser.create(transfer, member, BigDecimal.ZERO));
        entityManager.flush();
        entityManager.clear();

        // when
        TransferDetailResponseDto response = transferService.searchTransfer(member.getId(), travelItinerary.getId());

        // then
        assertThat(response.remainingAmount()).isEqualByComparingTo("0");
        assertThat(response.isDone()).isTrue();
    }

    @Test
    @DisplayName("여행 일정 멤버가 아니면 청구서를 조회할 수 없다.")
    void 여행_일정_멤버가_아니면_청구서를_조회할_수_없다() {
        // given
        User creator = saveUser("forbidden-creator");
        User member = saveUser("forbidden-member");
        User outsider = saveUser("forbidden-outsider");
        Group group = saveGroup("권한 검증 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "권한 검증 여행");
        saveTravelMembership(creator, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);
        Transfer transfer = saveTransfer(creator, group, travelItinerary, TransferStatus.UNCONFIRM);
        transferUserJpaRepository.save(TransferUser.create(transfer, member, new BigDecimal("10000")));
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> transferService.searchTransfer(outsider.getId(), travelItinerary.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("여행장(LEADER)은 UNCONFIRM 상태 청구서의 메타 정보를 수정할 수 있다.")
    void 여행장_LEADER은_UNCONFIRM_상태_청구서의_메타_정보를_수정할_수_있다() {
        // given
        User leader = saveUser("leader-update");
        Group group = saveGroup("수정 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "수정 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 제목");
        TransferUpdateRequestDto request = new TransferUpdateRequestDto(
                "수정된 제목",
                "수정된 설명",
                LocalDateTime.of(2030, 4, 1, 18, 0)
        );

        // when
        TransferUpdateResponseDto response = transferService.updateMetaInfo(leader.getId(), transfer.getId(), request);

        // then
        assertThat(response.transferId()).isEqualTo(transfer.getId());
        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(response.description()).isEqualTo("수정된 설명");
        assertThat(response.dueAt()).isEqualTo(LocalDateTime.of(2030, 4, 1, 18, 0));
        assertThat(response.transferStatus()).isEqualTo(TransferStatus.UNCONFIRM);

        Transfer updatedTransfer = transferRepository.findById(transfer.getId()).orElseThrow();
        assertThat(updatedTransfer.getTitle()).isEqualTo("수정된 제목");
        assertThat(updatedTransfer.getDescription()).isEqualTo("수정된 설명");
        assertThat(updatedTransfer.getDueAt()).isEqualTo(LocalDateTime.of(2030, 4, 1, 18, 0));
    }

    @Test
    @DisplayName("UNCONFIRM 상태가 아닌 청구서는 수정할 수 없다.")
    void UNCONFIRM_상태가_아닌_청구서는_수정할_수_없다() {
        // given
        User leader = saveUser("leader-confirm");
        Group group = saveGroup("확정 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확정 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.CONFIRM, "확정 제목");
        TransferUpdateRequestDto request = new TransferUpdateRequestDto(
                "수정 시도",
                "수정 시도 설명",
                LocalDateTime.of(2030, 4, 2, 18, 0)
        );

        // when & then
        assertThatThrownBy(() -> transferService.updateMetaInfo(leader.getId(), transfer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.INVOICE_UPDATE_NOT_ALLOWED_STATUS);
                });
    }

    @Test
    @DisplayName("그룹 멤버가 아니면 청구서 메타 정보를 수정할 수 없다.")
    void 그룹_멤버가_아니면_청구서_메타_정보를_수정할_수_없다() {
        // given
        User leader = saveUser("leader-not-member");
        User outsider = saveUser("outsider-not-member");
        Group group = saveGroup("멤버 검증 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "멤버 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 제목");
        TransferUpdateRequestDto request = new TransferUpdateRequestDto(
                "수정 시도",
                "수정 시도 설명",
                LocalDateTime.of(2030, 4, 3, 18, 0)
        );

        // when & then
        assertThatThrownBy(() -> transferService.updateMetaInfo(outsider.getId(), transfer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.NOT_GROUP_MEMBER);
                });
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 청구서 메타 정보를 수정할 수 없다.")
    void 여행장_LEADER가_아니면_청구서_메타_정보를_수정할_수_없다() {
        // given
        User leader = saveUser("leader-meta");
        User member = saveUser("member-meta");
        Group group = saveGroup("리더 검증 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "리더 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 제목");
        TransferUpdateRequestDto request = new TransferUpdateRequestDto(
                "수정 시도",
                "수정 시도 설명",
                LocalDateTime.of(2030, 4, 4, 18, 0)
        );

        // when & then
        assertThatThrownBy(() -> transferService.updateMetaInfo(member.getId(), transfer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.NOT_TRAVEL_LEADER);
                });
    }

    @Test
    @DisplayName("여행장(LEADER)은 청구서 금액/대상 정보를 수정할 수 있다.")
    void 여행장_LEADER은_청구서_금액_대상_정보를_수정할_수_있다() {
        // given
        User leader = saveUser("leader-adjust");
        User member1 = saveUser("member-adjust-1");
        User member2 = saveUser("member-adjust-2");
        Group group = saveGroup("정산 수정 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(member1, group, Role.MEMBER);
        saveUserGroup(member2, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "정산 수정 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member1, travelItinerary, UserRole.MEMBER);
        saveTravelMembership(member2, travelItinerary, UserRole.MEMBER);

        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 청구서");
        transferUserJpaRepository.save(TransferUser.create(transfer, member1, new BigDecimal("70000")));

        TransferAdjustRequestDto request = createAdjustRequest(
                new BigDecimal("30000"),
                List.of(
                        new RecipientAmountDto(member1.getPublicUuid().toString(), new BigDecimal("10000")),
                        new RecipientAmountDto(member2.getPublicUuid().toString(), new BigDecimal("20000"))
                )
        );

        // when
        TransferAdjustResponseDto response = transferService.updateInfo(leader.getId(), transfer.getId(), request);

        // then
        assertThat(response.transferId()).isEqualTo(transfer.getId());
        assertThat(response.totalAmount()).isEqualByComparingTo("30000");
        assertThat(response.members()).hasSize(2);

        Transfer updatedTransfer = transferRepository.findById(transfer.getId()).orElseThrow();
        assertThat(updatedTransfer.getTotalAmount()).isEqualByComparingTo("30000");

        List<TransferUser> updatedUsers = transferUserJpaRepository.findAll().stream()
                .filter(iu -> iu.getTransfer().getId().equals(transfer.getId()))
                .toList();
        assertThat(updatedUsers).hasSize(2);
        assertThat(updatedUsers).extracting(iu -> iu.getUser().getId())
                .containsExactlyInAnyOrder(member1.getId(), member2.getId());
    }

    @Test
    @DisplayName("청구서 금액/대상 정보 수정 시 총 금액 합계가 다르면 INVALID_TOTAL_AMOUNT 예외가 발생한다.")
    void 청구서_금액_대상_정보_수정_시_총_금액_합계가_다르면_INVALID_TOTAL_AMOUNT_예외가_발생한다() {
        // given
        User leader = saveUser("leader-adjust-sum");
        User member = saveUser("member-adjust-sum");
        Group group = saveGroup("정산 합계 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "정산 합계 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);

        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 청구서");

        TransferAdjustRequestDto request = createAdjustRequest(
                new BigDecimal("50000"),
                List.of(new RecipientAmountDto(member.getPublicUuid().toString(), new BigDecimal("30000")))
        );

        // when & then
        assertThatThrownBy(() -> transferService.updateInfo(leader.getId(), transfer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.INVALID_TOTAL_AMOUNT);
                });
    }

    @Test
    @DisplayName("청구서 금액/대상 정보 수정 시 정산 완료 멤버 금액이 양수면 INVALID_SETTLED_AMOUNT 예외가 발생한다.")
    void 청구서_금액_대상_정보_수정_시_정산_완료_멤버_금액이_양수면_INVALID_SETTLED_AMOUNT_예외가_발생한다() {
        // given
        User leader = saveUser("leader-adjust-settled");
        User member = saveUser("member-adjust-settled");
        Group group = saveGroup("정산 상태 검증 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "정산 상태 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 청구서");

        TransferAdjustRequestDto request = new TransferAdjustRequestDto(
                "999999-00-999999",
                "KB국민",
                "김민준",
                new BigDecimal("30000"),
                List.of(
                        new TransferAdjustRequestDto.MemberDto(
                                member.getPublicUuid().toString(),
                                "멤버",
                                "http://img",
                                new BigDecimal("30000"),
                                true
                        )
                )
        );

        // when & then
        assertThatThrownBy(() -> transferService.updateInfo(leader.getId(), transfer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.INVALID_SETTLED_AMOUNT);
                });
    }

    @Test
    @DisplayName("청구서 금액/대상 정보 수정 시 수신자 중 그룹 멤버가 아니면 NOT_GROUP_MEMBER 예외가 발생한다.")
    void 청구서_금액_대상_정보_수정_시_수신자_중_그룹_멤버가_아니면_NOT_GROUP_MEMBER_예외가_발생한다() {
        // given
        User leader = saveUser("leader-adjust-outsider");
        User outsider = saveUser("outsider-adjust");
        Group group = saveGroup("정산 멤버 검증 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "정산 멤버 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);

        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 청구서");

        TransferAdjustRequestDto request = createAdjustRequest(
                new BigDecimal("10000"),
                List.of(new RecipientAmountDto(outsider.getPublicUuid().toString(), new BigDecimal("10000")))
        );

        // when & then
        assertThatThrownBy(() -> transferService.updateInfo(leader.getId(), transfer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.NOT_GROUP_MEMBER);
                });
    }

    @Test
    @DisplayName("청구서 금액/대상 정보 수정 시 수신자에 중복 사용자가 있으면 DUPLICATE_RECIPIENT 예외가 발생한다.")
    void 청구서_금액_대상_정보_수정_시_수신자에_중복_사용자가_있으면_DUPLICATE_RECIPIENT_예외가_발생한다() {
        // given
        User leader = saveUser("leader-adjust-dup");
        User member = saveUser("member-adjust-dup");
        Group group = saveGroup("정산 중복 수신자 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "정산 중복 수신자 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 청구서");

        TransferAdjustRequestDto request = createAdjustRequest(
                new BigDecimal("20000"),
                List.of(
                        new RecipientAmountDto(member.getPublicUuid().toString(), new BigDecimal("10000")),
                        new RecipientAmountDto(member.getPublicUuid().toString(), new BigDecimal("10000"))
                )
        );

        // when & then
        assertThatThrownBy(() -> transferService.updateInfo(leader.getId(), transfer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.DUPLICATE_RECIPIENT);
                });
    }

    @Test
    @DisplayName("여행장(LEADER)은 청구서를 확인(CONFIRM)할 수 있다.")
    void 여행장_LEADER은_청구서를_확인할_수_있다() {
        // given
        User leader = saveUser("leader-check");
        Group group = saveGroup("확인 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확인 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "확인 대상 청구서");

        // when
        transferService.check(leader.getId(), transfer.getId());

        // then
        Transfer confirmedTransfer = transferRepository.findById(transfer.getId()).orElseThrow();
        assertThat(confirmedTransfer.getTransferStatus()).isEqualTo(TransferStatus.CONFIRM);
    }

    @Test
    @DisplayName("UNCONFIRM 상태가 아니면 청구서를 확인할 수 없다.")
    void UNCONFIRM_상태가_아니면_청구서를_확인할_수_없다() {
        // given
        User leader = saveUser("leader-check-status");
        Group group = saveGroup("확인 상태 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확인 상태 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.CONFIRM, "이미 확정된 청구서");

        // when & then
        assertThatThrownBy(() -> transferService.check(leader.getId(), transfer.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.INVOICE_CHECK_NOT_ALLOWED_STATUS);
                });
    }

    @Test
    @DisplayName("그룹 멤버가 아니면 청구서를 확인할 수 없다.")
    void 그룹_멤버가_아니면_청구서를_확인할_수_없다() {
        // given
        User leader = saveUser("leader-check-group-member");
        User outsider = saveUser("outsider-check-group-member");
        Group group = saveGroup("확인 그룹 멤버 검증 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확인 그룹 멤버 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "확인 대상 청구서");

        // when & then
        assertThatThrownBy(() -> transferService.check(outsider.getId(), transfer.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(GroupErrorCode.NOT_GROUP_MEMBER);
                });
    }

    @Test
    @DisplayName("여행 멤버십이 없으면 청구서를 확인할 수 없다.")
    void 여행_멤버십이_없으면_청구서를_확인할_수_없다() {
        // given
        User leader = saveUser("leader-check-travel-membership");
        User groupMemberWithoutTravelMembership = saveUser("group-member-without-travel-membership");
        Group group = saveGroup("확인 여행 멤버십 검증 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(groupMemberWithoutTravelMembership, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확인 여행 멤버십 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "확인 대상 청구서");

        // when & then
        assertThatThrownBy(() -> transferService.check(groupMemberWithoutTravelMembership.getId(), transfer.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("여행장(LEADER)이 아니면 청구서를 확인할 수 없다.")
    void 여행장_LEADER가_아니면_청구서를_확인할_수_없다() {
        // given
        User leader = saveUser("leader-check-not-leader");
        User member = saveUser("member-check-not-leader");
        Group group = saveGroup("확인 리더 권한 검증 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        saveUserGroup(member, group, Role.MEMBER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "확인 리더 권한 검증 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        saveTravelMembership(member, travelItinerary, UserRole.MEMBER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "확인 대상 청구서");

        // when & then
        assertThatThrownBy(() -> transferService.check(member.getId(), transfer.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.NOT_TRAVEL_LEADER);
                });
    }

    @Test
    @DisplayName("존재하지 않는 청구서는 확인할 수 없다.")
    void 존재하지_않는_청구서는_확인할_수_없다() {
        // given
        User leader = saveUser("leader-check-not-found");

        // when & then
        assertThatThrownBy(() -> transferService.check(leader.getId(), 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.NOT_FOUND_INVOICE);
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동일한 청구서 메타 수정 요청이 동시에 들어오면 하나만 성공하고 나머지는 CONCURRENT_INVOICE_UPDATE가 발생한다.")
    void 동일한_청구서_메타_수정_요청이_동시에_들어오면_하나만_성공하고_나머지는_CONCURRENT_INVOICE_UPDATE가_발생한다() throws InterruptedException {
        // given
        User leader = saveUser("leader-update-concurrency");
        Group group = saveGroup("동시 수정 그룹");
        saveUserGroup(leader, group, Role.OWNER);
        TravelItinerary travelItinerary = saveTravelItinerary(group, "동시 수정 여행");
        saveTravelMembership(leader, travelItinerary, UserRole.LEADER);
        Transfer transfer = saveTransfer(group, leader, travelItinerary, TransferStatus.UNCONFIRM, "기존 제목");

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable firstUpdate = () -> {
            ready.countDown();
            try {
                start.await();
                transferService.updateMetaInfo(
                        leader.getId(),
                        transfer.getId(),
                        new TransferUpdateRequestDto("수정 제목 A", "수정 설명 A", LocalDateTime.of(2030, 4, 10, 18, 0))
                );
                successCount.incrementAndGet();
            } catch (Throwable throwable) {
                failures.add(throwable);
            } finally {
                done.countDown();
            }
        };

        Runnable secondUpdate = () -> {
            ready.countDown();
            try {
                start.await();
                transferService.updateMetaInfo(
                        leader.getId(),
                        transfer.getId(),
                        new TransferUpdateRequestDto("수정 제목 B", "수정 설명 B", LocalDateTime.of(2030, 4, 11, 18, 0))
                );
                successCount.incrementAndGet();
            } catch (Throwable throwable) {
                failures.add(throwable);
            } finally {
                done.countDown();
            }
        };

        try {
            executorService.submit(firstUpdate);
            executorService.submit(secondUpdate);

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

            long concurrentConflictCount = failures.stream()
                    .filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .filter(e -> e.getErrorCode() == TransferErrorCode.CONCURRENT_INVOICE_UPDATE)
                    .count();

            Transfer updatedTransfer = transferRepository.findById(transfer.getId()).orElseThrow();

            assertThat(successCount.get()).isBetween(1, 2);
            assertThat(failures.size()).isBetween(0, 1);
            assertThat(successCount.get() + failures.size()).isEqualTo(2);
            assertThat(concurrentConflictCount).isEqualTo(failures.size());
            assertThat(List.of("수정 제목 A", "수정 제목 B")).contains(updatedTransfer.getTitle());
        } finally {
            executorService.shutdownNow();
            transferUserJpaRepository.deleteAllInBatch();
            transferRepository.deleteAllInBatch();
            userTravelItineraryJpaRepository.deleteAllInBatch();
            travelItineraryJpaRepository.deleteAllInBatch();
            userGroupJpaRepository.deleteAllInBatch();
            groupJpaRepository.deleteAllInBatch();
            userJpaRepository.deleteAllInBatch();
        }
    }

    @Test
    @DisplayName("청구서 삭제 요청 시 상태를 DELETED로 변경하고 transferUser를 모두 삭제한다.")
    void 청구서_삭제_성공() {
        User creator = saveUser("creator");
        User member = saveUser("member-delete-success");
        Group group = saveGroup("삭제 테스트 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "삭제 테스트 여행");
        Transfer transfer = saveTransfer(creator, group, travelItinerary, TransferStatus.UNCONFIRM);
        transferUserJpaRepository.save(TransferUser.create(transfer, member, new BigDecimal("10000")));

        transferService.delete(creator.getId(), transfer.getId());

        Transfer deletedTransfer = transferRepository.findById(transfer.getId()).orElseThrow();
        assertThat(deletedTransfer.getTransferStatus()).isEqualTo(TransferStatus.DELETED);
        assertThat(transferUserJpaRepository.findAll().stream()
                .filter(transferUser -> transferUser.getTransfer().getId().equals(transfer.getId()))
                .toList()).isEmpty();
    }

    @Test
    @DisplayName("청구서 생성자가 아니면 삭제 시 예외를 던진다.")
    void 청구서_생성자가_아니면_삭제_불가() {
        User creator = saveUser("creator-not-owner");
        User other = saveUser("other-not-owner");
        Group group = saveGroup("삭제 권한 테스트 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "삭제 권한 테스트 여행");
        Transfer transfer = saveTransfer(creator, group, travelItinerary, TransferStatus.UNCONFIRM);

        assertThatThrownBy(() -> transferService.delete(other.getId(), transfer.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.DELETE_UNAUTHORIZED);
                });
    }

    @Test
    @DisplayName("청구서 상태가 UNCONFIRM이 아니면 삭제 시 예외를 던진다.")
    void 청구서_상태가_UNCONFIRM이_아니면_삭제_불가() {
        User creator = saveUser("creator-status");
        Group group = saveGroup("삭제 상태 테스트 그룹");
        TravelItinerary travelItinerary = saveTravelItinerary(group, "삭제 상태 테스트 여행");
        Transfer transfer = saveTransfer(creator, group, travelItinerary, TransferStatus.CONFIRM);

        assertThatThrownBy(() -> transferService.delete(creator.getId(), transfer.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(TransferErrorCode.DELETE_FORBIDDEN_STATUS);
                });
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

    private Group saveGroup(final String groupName) {
        return groupJpaRepository.save(
                Group.create(
                        GroupKind.PUBLIC,
                        groupName,
                        "설명",
                        "http://thumb",
                        10
                )
        );
    }

    private UserGroup saveUserGroup(final User user, final Group group, final Role role) {
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

    private TransferCreateRequestDto createRequest(
            final Long groupId,
            final Long travelItineraryId,
            final List<RecipientAmountDto> recipients,
            final String title,
            final String description,
            final BigDecimal totalAmount,
            final LocalDateTime dueAt
    ) {
        List<TransferCreateRequestDto.MemberDto> members = recipients.stream()
                .map(recipient -> new TransferCreateRequestDto.MemberDto(
                        recipient.userId(),
                        "멤버",
                        "http://img",
                        recipient.amount(),
                        recipient.amount().compareTo(BigDecimal.ZERO) == 0
                ))
                .toList();

        return new TransferCreateRequestDto(
                "999999-00-999999",
                "KB국민",
                "김민준",
                totalAmount,
                members,
                groupId,
                travelItineraryId,
                title,
                description,
                dueAt
        );
    }

    private TransferAdjustRequestDto createAdjustRequest(
            final BigDecimal totalAmount,
            final List<RecipientAmountDto> recipients
    ) {
        List<TransferAdjustRequestDto.MemberDto> members = recipients.stream()
                .map(recipient -> new TransferAdjustRequestDto.MemberDto(
                        recipient.userId(),
                        "멤버",
                        "http://img",
                        recipient.amount(),
                        recipient.amount().compareTo(BigDecimal.ZERO) == 0
                ))
                .toList();

        return new TransferAdjustRequestDto(
                "999999-00-999999",
                "KB국민",
                "김민준",
                totalAmount,
                members
        );
    }

    private Transfer saveTransfer(
            final Group group,
            final User creator,
            final TravelItinerary travelItinerary,
            final TransferStatus transferStatus,
            final String title
    ) {
        return transferRepository.save(
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
                        .totalAmount(new BigDecimal("70000"))
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
        return transferRepository.save(
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
                        .totalAmount(new BigDecimal("10000"))
                        .dueAt(LocalDateTime.of(2030, 3, 31, 18, 0))
                        .build()
        );
    }
}
