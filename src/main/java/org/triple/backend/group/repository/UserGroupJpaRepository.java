package org.triple.backend.group.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;

public interface UserGroupJpaRepository extends JpaRepository<UserGroup, Long> {

    boolean existsByGroupIdAndUserIdAndRole(Long groupId, Long userId, Role role);

    boolean existsByGroupIdAndUserIdAndJoinStatus(Long groupId, Long userId, JoinStatus joinStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserGroup ug WHERE ug.group.id = :groupId")
    void bulkDeleteByGroupId(Long groupId);
}
