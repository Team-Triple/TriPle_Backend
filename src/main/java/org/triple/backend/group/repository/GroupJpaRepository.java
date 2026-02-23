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
    @Query("SELECT g FROM Group g WHERE g.id = :groupId")
    Optional<Group> findByIdForUpdate(Long groupId);

    @Query("SELECT g FROM Group g WHERE g.groupKind = :groupKind order by g.id desc")
    List<Group> findPublicFirstPage(GroupKind groupKind, Pageable pageable);

    @Query("SELECT g FROM Group g WHERE g.groupKind = :groupKind AND g.id < :cursor ORDER BY g.id desc")
    List<Group> findPublicNextPage(GroupKind groupKind, Long cursor, Pageable pageable);
}