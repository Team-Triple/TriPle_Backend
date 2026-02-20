package org.triple.backend.group.unit.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.common.annotation.RepositoryTest;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.repository.GroupJpaRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.triple.backend.group.fixture.GroupFixtures.privateGroup;
import static org.triple.backend.group.fixture.GroupFixtures.publicGroup;

@RepositoryTest
public class GroupJpaRepositoryTest {

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("저장한 그룹을 ID로 조회시 Group 엔티티를 반환한다.")
    void 저장한_그룹을_ID로_조회시_Group_엔티티를_반환한다() {
        // given
        Group group = Group.create(
                GroupKind.PUBLIC,
                "여행모임",
                "3월 일본 여행",
                "https://example.com/thumb.png",
                10
        );

        // when
        Group saved = groupJpaRepository.save(group);

        // then
        assertThat(saved.getId()).isNotNull();

        Group found = groupJpaRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("여행모임");
        assertThat(found.getDescription()).isEqualTo("3월 일본 여행");
        assertThat(found.getMemberLimit()).isEqualTo(10);
        assertThat(found.getGroupKind()).isEqualTo(GroupKind.PUBLIC);
        assertThat(found.getThumbNailUrl()).isEqualTo("https://example.com/thumb.png");
        assertThat(found.getCurrentMemberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 그룹이면 empty를 반환한다")
    void 존재하지_않는_사용자면_empty를_반환한다() {
        // when
        var result = groupJpaRepository.findById(999999L);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("PUBLIC 첫 페이지 조회시 PUBLIC만 id 내림차순으로 size만큼 조회된다")
    void 첫_페이지_조회시_PUBLIC만_id_내림차순으로_size_만큼_조회한다() {
        // given
        for (int i = 1; i <= 12; i++) {
            groupJpaRepository.save(publicGroup("public-" + i));
        }
        for (int i = 1; i <= 3; i++) {
            groupJpaRepository.save(privateGroup("private-" + i));
        }

        Pageable pageable = PageRequest.of(0, 10);

        // when
        List<Group> result = groupJpaRepository.findPublicFirstPage(GroupKind.PUBLIC, pageable);

        // then
        assertThat(result).hasSize(10);
        assertThat(result).allSatisfy(g -> assertThat(g.getGroupKind()).isEqualTo(GroupKind.PUBLIC));

        List<Long> ids = result.stream().map(Group::getId).toList();
        assertThat(ids).isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }


    @Test
    @DisplayName("PUBLIC 다음 페이지 조회: cursor 보다 작은 id만 id 내림차순으로 조회된다")
    void PUBLIC_다음_페이지_조회_cursor보다_작은_id만_id_내림차순으로_조회된다() {
        // given
        for (int i = 1; i <= 15; i++) {
            groupJpaRepository.save(publicGroup("public-" + i));
        }

        Pageable firstPageable = PageRequest.of(0, 10);
        List<Group> first = groupJpaRepository.findPublicFirstPage(GroupKind.PUBLIC, firstPageable);

        Long cursor = first.get(first.size() - 1).getId();

        Pageable nextPageable = PageRequest.of(0, 10);

        // when
        List<Group> next = groupJpaRepository.findPublicNextPage(GroupKind.PUBLIC, cursor, nextPageable);

        // then
        assertThat(next).hasSize(5);
        assertThat(next).allSatisfy(g -> {
            assertThat(g.getGroupKind()).isEqualTo(GroupKind.PUBLIC);
            assertThat(g.getId()).isLessThan(cursor);
        });

        List<Long> ids = next.stream().map(Group::getId).toList();
        assertThat(ids).isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }

    @Test
    @DisplayName("그룹 삭제 시 Group 엔티티가 삭제된다")
    void 그룹_삭제_시_Group_엔티티가_삭제된다() {
        // given
        Group group = groupJpaRepository.saveAndFlush(
                Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10)
        );
        Long groupId = group.getId();

        // when
        groupJpaRepository.deleteById(groupId);
        groupJpaRepository.flush();

        // then
        assertThat(groupJpaRepository.findById(groupId)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("같은 버전의 그룹을 동시에 수정하면 늦게 커밋한 트랜잭션은 낙관락 예외가 발생한다")
    void 같은_버전의_그룹을_동시에_수정하면_늦게_커밋한_트랜잭션은_낙관락_예외가_발생한다() {
        // given
        Group base = groupJpaRepository.saveAndFlush(
                Group.create(GroupKind.PUBLIC, "기존모임", "기존설명", "https://example.com/original.png", 10)
        );

        EntityManager em1 = entityManagerFactory.createEntityManager();
        EntityManager em2 = entityManagerFactory.createEntityManager();

        try {
            em1.getTransaction().begin();
            em2.getTransaction().begin();

            Group tx1Group = em1.find(Group.class, base.getId());
            Group tx2Group = em2.find(Group.class, base.getId());

            tx1Group.update(Group.create(GroupKind.PRIVATE, "수정-1", "설명-1", "https://example.com/1.png", 20));
            em1.flush();
            em1.getTransaction().commit();

            tx2Group.update(Group.create(GroupKind.PUBLIC, "수정-2", "설명-2", "https://example.com/2.png", 30));

            boolean conflictRaised = false;
            try {
                em2.flush();
                em2.getTransaction().commit();
            } catch (RuntimeException e) {
                conflictRaised = hasOptimisticConflictCause(e);
                if (em2.getTransaction().isActive()) {
                    em2.getTransaction().rollback();
                }
            }

            assertThat(conflictRaised).isTrue();
        } finally {
            if (em1.getTransaction().isActive()) {
                em1.getTransaction().rollback();
            }
            em1.close();
            em2.close();
        }

        Group result = groupJpaRepository.findById(base.getId()).orElseThrow();
        assertThat(result.getName()).isEqualTo("수정-1");
        assertThat(result.getDescription()).isEqualTo("설명-1");
        assertThat(result.getThumbNailUrl()).isEqualTo("https://example.com/1.png");
        assertThat(result.getMemberLimit()).isEqualTo(20);
        assertThat(result.getGroupKind()).isEqualTo(GroupKind.PRIVATE);
    }

    private boolean hasOptimisticConflictCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OptimisticLockException) {
                return true;
            }
            String simpleName = current.getClass().getSimpleName();
            if ("StaleObjectStateException".equals(simpleName)
                    || "ObjectOptimisticLockingFailureException".equals(simpleName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
