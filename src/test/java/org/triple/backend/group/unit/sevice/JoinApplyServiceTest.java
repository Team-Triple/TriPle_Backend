package org.triple.backend.group.unit.sevice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.joinApply.JoinApplyStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.exception.JoinApplyErrorCode;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.JoinApplyJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.group.service.JoinApplyService;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

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
@Import(JoinApplyService.class)
public class JoinApplyServiceTest {

    @Autowired
    private JoinApplyService joinApplyService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private JoinApplyJpaRepository joinApplyJpaRepository;

    @Autowired
    private UserGroupJpaRepository userGroupJpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("가입 신청을 성공하면 PENDING 상태의 신청이 저장된다")
    void 가입_신청을_성공하면_PENDING_상태의_신청이_저장된다() {
        // given
        User applicant = userJpaRepository.save(User.builder()
                .providerId("kakao-applicant")
                .nickname("지원자")
                .email("applicant@test.com")
                .profileUrl("http://img")
                .build());

        Group group = groupJpaRepository.save(
                Group.create(GroupKind.PUBLIC, "여행모임", "설명", "https://example.com/thumb.png", 10)
        );

        // when
        joinApplyService.joinApply(group.getId(), applicant.getId());

        // then
        assertThat(joinApplyJpaRepository.count()).isEqualTo(1);
        JoinApply savedApply = joinApplyJpaRepository.findByGroupIdAndUserId(group.getId(), applicant.getId()).orElseThrow();
        assertThat(savedApply.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.PENDING);
    }

    @Test
    @DisplayName("이미 가입 신청한 그룹에 다시 신청하면 예외가 발생한다")
    void 이미_가입_신청한_그룹에_다시_신청하면_예외가_발생한다() {
        // given
        User applicant = userJpaRepository.save(User.builder()
                .providerId("kakao-applicant")
                .nickname("지원자")
                .email("applicant@test.com")
                .profileUrl("http://img")
                .build());

        Group group = groupJpaRepository.save(
                Group.create(GroupKind.PUBLIC, "여행모임", "설명", "https://example.com/thumb.png", 10)
        );

        joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, group));

        // when & then
        assertThatThrownBy(() -> joinApplyService.joinApply(group.getId(), applicant.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(JoinApplyErrorCode.ALREADY_APPLY_JOIN_REQUEST);
                });
    }

    @Test
    @DisplayName("취소된 가입 신청은 재신청할 수 있다")
    void 취소된_가입_신청은_재신청할_수_있다() {
        // given
        User applicant = userJpaRepository.save(User.builder()
                .providerId("kakao-applicant")
                .nickname("지원자")
                .email("applicant@test.com")
                .profileUrl("http://img")
                .build());

        Group group = groupJpaRepository.save(
                Group.create(GroupKind.PUBLIC, "여행모임", "설명", "https://example.com/thumb.png", 10)
        );

        JoinApply joinApply = joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, group));
        joinApply.cancel();
        joinApplyJpaRepository.saveAndFlush(joinApply);

        // when
        joinApplyService.joinApply(group.getId(), applicant.getId());

        // then
        JoinApply reapplied = joinApplyJpaRepository.findByGroupIdAndUserId(group.getId(), applicant.getId()).orElseThrow();
        assertThat(reapplied.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.PENDING);
        assertThat(joinApplyJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("거절된 가입 신청은 재신청할 수 없다")
    void 거절된_가입_신청은_재신청할_수_없다() {
        // given
        User applicant = userJpaRepository.save(User.builder()
                .providerId("kakao-applicant")
                .nickname("지원자")
                .email("applicant@test.com")
                .profileUrl("http://img")
                .build());

        Group group = groupJpaRepository.save(
                Group.create(GroupKind.PUBLIC, "여행모임", "설명", "https://example.com/thumb.png", 10)
        );

        JoinApply joinApply = joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, group));
        joinApply.reject();
        joinApplyJpaRepository.flush();

        // when & then
        assertThatThrownBy(() -> joinApplyService.joinApply(group.getId(), applicant.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(JoinApplyErrorCode.REAPPLY_ALLOWED_ONLY_CANCELED);
                });
    }

    @Test
    @DisplayName("이미 그룹 멤버인 유저는 가입 신청할 수 없다")
    void 이미_그룹_멤버인_유저는_가입_신청할_수_없다() {
        // given
        User member = userJpaRepository.save(User.builder()
                .providerId("kakao-member")
                .nickname("멤버")
                .email("member@test.com")
                .profileUrl("http://img")
                .build());

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "https://example.com/thumb.png", 10);
        group.addMember(member, Role.MEMBER);
        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        // when & then
        assertThatThrownBy(() -> joinApplyService.joinApply(savedGroup.getId(), member.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(JoinApplyErrorCode.ALREADY_JOINED_GROUP);
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동일 유저의 동시 가입 신청은 하나만 성공한다")
    void 동일_유저의_동시_가입_신청은_하나만_성공한다() throws InterruptedException {
        // given
        User applicant = userJpaRepository.save(User.builder()
                .providerId("kakao-applicant")
                .nickname("지원자")
                .email("applicant@test.com")
                .profileUrl("http://img")
                .build());

        Group group = groupJpaRepository.save(
                Group.create(GroupKind.PUBLIC, "여행모임", "설명", "https://example.com/thumb.png", 10)
        );

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable applyTask = () -> {
            ready.countDown();
            try {
                start.await();
                joinApplyService.joinApply(group.getId(), applicant.getId());
                successCount.incrementAndGet();
            } catch (Throwable throwable) {
                failures.add(throwable);
            } finally {
                done.countDown();
            }
        };

        try {
            executorService.submit(applyTask);
            executorService.submit(applyTask);

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

            long conflictCount = failures.stream()
                    .filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .filter(e -> e.getErrorCode() == JoinApplyErrorCode.ALREADY_APPLY_JOIN_REQUEST)
                    .count();

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);
            assertThat(joinApplyJpaRepository.count()).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
            joinApplyJpaRepository.deleteAllInBatch();
            userGroupJpaRepository.deleteAllInBatch();
            groupJpaRepository.deleteAllInBatch();
            userJpaRepository.deleteAllInBatch();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("부모 Group 락이 선점되면 가입 신청은 락 해제 후 처리된다")
    void 부모_Group_락이_선점되면_가입_신청은_락_해제_후_처리된다() throws InterruptedException {
        // given
        User applicant = userJpaRepository.save(User.builder()
                .providerId("kakao-lock-applicant")
                .nickname("지원자")
                .email("lock-applicant@test.com")
                .profileUrl("http://img")
                .build());

        Group group = groupJpaRepository.save(
                Group.create(GroupKind.PUBLIC, "락테스트모임", "설명", "https://example.com/thumb.png", 10)
        );

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch applyDone = new CountDownLatch(1);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable lockHolderTask = () -> {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    groupJpaRepository.findByIdForUpdate(group.getId()).orElseThrow();
                    lockAcquired.countDown();
                    try {
                        releaseLock.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            } catch (Throwable throwable) {
                failures.add(throwable);
                lockAcquired.countDown();
            }
        };

        Runnable applyTask = () -> {
            try {
                joinApplyService.joinApply(group.getId(), applicant.getId());
            } catch (Throwable throwable) {
                failures.add(throwable);
            } finally {
                applyDone.countDown();
            }
        };

        try {
            executorService.submit(lockHolderTask);

            assertThat(lockAcquired.await(3, TimeUnit.SECONDS)).isTrue();

            executorService.submit(applyTask);

            assertThat(applyDone.await(300, TimeUnit.MILLISECONDS)).isFalse();

            releaseLock.countDown();

            assertThat(applyDone.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failures).isEmpty();

            JoinApply savedApply = joinApplyJpaRepository.findByGroupIdAndUserId(group.getId(), applicant.getId()).orElseThrow();
            assertThat(savedApply.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.PENDING);
        } finally {
            releaseLock.countDown();
            executorService.shutdownNow();
            joinApplyJpaRepository.deleteAllInBatch();
            userGroupJpaRepository.deleteAllInBatch();
            groupJpaRepository.deleteAllInBatch();
            userJpaRepository.deleteAllInBatch();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("취소된 신청에 대한 동시 재신청은 하나만 성공한다")
    void 취소된_신청에_대한_동시_재신청은_하나만_성공한다() throws InterruptedException {
        // given
        User applicant = userJpaRepository.save(User.builder()
                .providerId("kakao-reapply-applicant")
                .nickname("지원자")
                .email("reapply-applicant@test.com")
                .profileUrl("http://img")
                .build());

        Group group = groupJpaRepository.save(
                Group.create(GroupKind.PUBLIC, "재신청모임", "설명", "https://example.com/thumb.png", 10)
        );

        JoinApply joinApply = joinApplyJpaRepository.saveAndFlush(JoinApply.create(applicant, group));
        joinApply.cancel();
        joinApplyJpaRepository.saveAndFlush(joinApply);

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable reapplyTask = () -> {
            ready.countDown();
            try {
                start.await();
                joinApplyService.joinApply(group.getId(), applicant.getId());
                successCount.incrementAndGet();
            } catch (Throwable throwable) {
                failures.add(throwable);
            } finally {
                done.countDown();
            }
        };

        try {
            executorService.submit(reapplyTask);
            executorService.submit(reapplyTask);

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

            long conflictCount = failures.stream()
                    .filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .filter(e -> e.getErrorCode() == JoinApplyErrorCode.ALREADY_APPLY_JOIN_REQUEST)
                    .count();

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);
            assertThat(joinApplyJpaRepository.count()).isEqualTo(1);

            JoinApply reapplied = joinApplyJpaRepository.findByGroupIdAndUserId(group.getId(), applicant.getId()).orElseThrow();
            assertThat(reapplied.getJoinApplyStatus()).isEqualTo(JoinApplyStatus.PENDING);
        } finally {
            executorService.shutdownNow();
            joinApplyJpaRepository.deleteAllInBatch();
            userGroupJpaRepository.deleteAllInBatch();
            groupJpaRepository.deleteAllInBatch();
            userJpaRepository.deleteAllInBatch();
        }
    }
}
