package org.triple.backend.group.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.group.entity.joinApply.JoinApply;

public interface JoinApplyJpaRepository extends JpaRepository<JoinApply, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM JoinApply ja WHERE ja.group.id = :groupId")
    int bulkDeleteByGroupId(Long groupId);
}
