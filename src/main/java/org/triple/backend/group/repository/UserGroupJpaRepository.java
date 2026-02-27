package org.triple.backend.group.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;

import java.util.List;
import java.util.Optional;

public interface UserGroupJpaRepository extends JpaRepository<UserGroup, Long> {

    @Query("SELECT ug FROM UserGroup ug join fetch ug.user WHERE ug.group.id = :groupId AND ug.joinStatus = :joinStatus")
    List<UserGroup> findAllByGroupIdAndJoinStatus(Long groupId, JoinStatus joinStatus);

    boolean existsByGroupIdAndUserIdAndRoleAndJoinStatus(Long groupId, Long userId, Role role, JoinStatus status);

    boolean existsByGroupIdAndUserIdAndJoinStatus(Long groupId, Long userId, JoinStatus joinStatus);

    Optional<UserGroup> findByGroupIdAndUserIdAndJoinStatus(Long groupId, Long userId, JoinStatus joinStatus);

    @Query("SELECT ug FROM UserGroup ug WHERE ug.group.id = :groupId AND ug.user.id = :userId")
    Optional<UserGroup> findByGroupIdAndUserId(Long groupId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserGroup ug WHERE ug.group.id = :groupId")
    void bulkDeleteByGroupId(Long groupId);


    @Query("""
        SELECT g FROM UserGroup ug
        JOIN ug.group g ON ug.group.id = g.id
        WHERE ug.user.id = :userId 
            AND ug.joinStatus = :joinStatus
            AND g.id < :cursor
        ORDER BY g.id desc
    """)
    List<Group> findMyGroupsNextPage(Long userId, JoinStatus joinStatus, Long cursor, Pageable pageable);

    @Query("""
        SELECT g FROM UserGroup ug
        JOIN ug.group g ON ug.group.id = g.id
        WHERE ug.user.id = :userId
            AND ug.joinStatus = :joinStatus
        ORDER BY g.id desc
    """)
    List<Group> findMyGroupsFirstPage(Long userId, JoinStatus joinStatus, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT ug
        FROM UserGroup ug
        JOIN FETCH ug.user
        WHERE ug.group.id = :groupId
         AND ug.joinStatus = :joinStatus
         AND ug.user.id IN :userIds
         ORDER BY ug.user.id
    """)
    List<UserGroup> findJoinedUsersInGroupForUpdate(Long groupId, JoinStatus joinStatus, List<Long> userIds);
}
