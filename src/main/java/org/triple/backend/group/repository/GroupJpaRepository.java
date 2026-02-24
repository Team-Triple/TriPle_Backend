package org.triple.backend.group.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;

import java.util.List;
import java.util.Optional;

public interface GroupJpaRepository extends JpaRepository<Group, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Group g WHERE g.id = :groupId")
    Optional<Group> findByIdForUpdate(Long groupId);

    @Query("SELECT g FROM Group g WHERE g.groupKind = :groupKind order by g.id desc")
    List<Group> findPublicFirstPage(GroupKind groupKind, Pageable pageable);

    @Query("SELECT g FROM Group g WHERE g.groupKind = :groupKind AND g.id < :cursor ORDER BY g.id desc")
    List<Group> findPublicNextPage(GroupKind groupKind, Long cursor, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT g FROM Group g WHERE g.id = :groupId")
    Optional<Group> findByIdForRead(Long groupId);


    @Query("""
            SELECT g
            FROM Group g
            WHERE g.groupKind = :kind
              AND (g.name LIKE CONCAT(:keyword, '%')
                OR g.description LIKE CONCAT('%', :keyword, '%'))
            ORDER BY g.id DESC
            """)
    List<Group> findFirstPageByKeywordLike(String keyword, Pageable pageable, GroupKind kind);

    @Query("""
            SELECT g
            FROM Group g
            WHERE g.id < :cursor
              AND g.groupKind = :kind
              AND (g.name LIKE CONCAT(:keyword, '%')
                OR g.description LIKE CONCAT('%', :keyword, '%'))
            ORDER BY g.id DESC
            """)
    List<Group> findNextPageByKeywordLike(String keyword, Long cursor, Pageable pageable, GroupKind kind);

    @Query(value = """
            SELECT g.*
            FROM travel_group g
            WHERE g.group_kind = :kind
              AND MATCH(g.name, g.description) AGAINST(:booleanQuery IN BOOLEAN MODE)
            ORDER BY g.group_id DESC
            """, nativeQuery = true)
    List<Group> findFirstPageByKeywordFullText(@Param("booleanQuery") String booleanQuery,
                                               @Param("kind") String kind,
                                               Pageable pageable);

    @Query(value = """
            SELECT g.*
            FROM travel_group g
            WHERE g.group_id < :cursor
              AND g.group_kind = :kind
              AND MATCH(g.name, g.description) AGAINST(:booleanQuery IN BOOLEAN MODE)
            ORDER BY g.group_id DESC
            """, nativeQuery = true)
    List<Group> findNextPageByKeywordFullText(@Param("booleanQuery") String booleanQuery,
                                              @Param("cursor") Long cursor,
                                              @Param("kind") String kind,
                                              Pageable pageable);
}
