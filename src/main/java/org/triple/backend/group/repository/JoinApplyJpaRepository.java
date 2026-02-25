package org.triple.backend.group.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.joinApply.JoinApplyStatus;

import java.util.Optional;

public interface JoinApplyJpaRepository extends JpaRepository<JoinApply, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM JoinApply ja WHERE ja.group.id = :groupId")
    int bulkDeleteByGroupId(Long groupId);

    Optional<JoinApply> findByGroupIdAndUserId(Long groupId, Long userId);

    @Query("SELECT ja FROM JoinApply ja JOIN FETCH ja.user WHERE ja.id = :id AND ja.group.id = :groupId AND ja.joinApplyStatus = :status")
    Optional<JoinApply> findByIdAndGroupIdAndJoinApplyStatus(Long id, Long groupId, JoinApplyStatus status);

    void deleteByGroupIdAndUserId(Long groupId, Long userId);
}
