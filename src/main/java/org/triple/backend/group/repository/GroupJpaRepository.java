package org.triple.backend.group.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;

import java.util.List;
import java.util.Optional;

public interface GroupJpaRepository extends JpaRepository<Group, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Group g WHERE g.id = :groupId AND g.isDeleted = false")
    Optional<Group> findByIdForUpdate(Long groupId);

    @Query("SELECT g FROM Group g WHERE g.groupKind = :groupKind AND g.isDeleted = false order by g.id desc")
    List<Group> findPublicFirstPage(GroupKind groupKind, Pageable pageable);

    @Query("SELECT g FROM Group g WHERE g.groupKind = :groupKind AND g.isDeleted = false AND g.id < :cursor ORDER BY g.id desc")
    List<Group> findPublicNextPage(GroupKind groupKind, Long cursor, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT g FROM Group g WHERE g.id = :groupId AND g.isDeleted = false")
    Optional<Group> findByIdForRead(Long groupId);

    @Query("SELECT g FROM Group g WHERE g.id = :groupId AND g.isDeleted = false")
    Optional<Group> findByIdAndIsDeletedFalse(Long groupId);

    boolean existsByIdAndIsDeletedFalse(Long groupId);

    @Query(value = """
            SELECT g.*
            FROM travel_group g
            WHERE g.group_kind = :kind
              AND g.is_deleted = false
              AND MATCH(g.name, g.description) AGAINST(:booleanQuery IN BOOLEAN MODE)
            ORDER BY g.group_id DESC
            """, nativeQuery = true)
    List<Group> findFirstPageByKeywordFullText(String booleanQuery,
                                               String kind,
                                               Pageable pageable);

    @Query(value = """
            SELECT g.*
            FROM travel_group g
            WHERE g.group_id < :cursor
              AND g.group_kind = :kind
              AND g.is_deleted = false
              AND MATCH(g.name, g.description) AGAINST(:booleanQuery IN BOOLEAN MODE)
            ORDER BY g.group_id DESC
            """, nativeQuery = true)
    List<Group> findNextPageByKeywordFullText(String booleanQuery,
                                              Long cursor,
                                              String kind,
                                              Pageable pageable);
}
