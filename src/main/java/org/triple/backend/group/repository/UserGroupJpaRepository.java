package org.triple.backend.group.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.userGroup.JoinStatus;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.user.entity.User;

public interface UserGroupJpaRepository extends JpaRepository<UserGroup, Long> {
    boolean existsByUserAndGroupAndJoinStatus(User user, Group group, JoinStatus joinStatus);
}
