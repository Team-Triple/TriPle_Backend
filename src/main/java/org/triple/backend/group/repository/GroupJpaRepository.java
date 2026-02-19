package org.triple.backend.group.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;

import java.util.List;

public interface GroupJpaRepository extends JpaRepository<Group, Long> {

    @Query("SELECT g FROM Group g WHERE g.groupKind = :groupKind order by g.id desc")
    List<Group> findPublicFirstPage(GroupKind groupKind, Pageable pageable);

    @Query("SELECT g FROM Group g WHERE g.groupKind = :groupKind AND g.id < :cursor ORDER BY g.id desc")
    List<Group> findPublicNextPage(GroupKind groupKind, Long cursor, Pageable pageable);
}