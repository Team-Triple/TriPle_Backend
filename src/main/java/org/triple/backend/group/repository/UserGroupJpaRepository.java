package org.triple.backend.group.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.user.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserGroupJpaRepository extends JpaRepository<UserGroup, Long> {

    @Query("SELECT ug FROM UserGroup ug join fetch ug.user WHERE ug.group.id = :groupId AND ug.joinStatus = :joinStatus")
    List<UserGroup> findAllByGroupIdAndJoinStatus(Long groupId, JoinStatus joinStatus);

    boolean existsByGroupIdAndUserIdAndRoleAndJoinStatus(Long groupId, Long userId, Role role, JoinStatus status);

    boolean existsByGroupIdAndUserIdAndJoinStatus(Long groupId, Long userId, JoinStatus joinStatus);

    Optional<UserGroup> findByGroupIdAndUserId(Long groupId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserGroup ug WHERE ug.group.id = :groupId")
    void bulkDeleteByGroupId(Long groupId);
}
