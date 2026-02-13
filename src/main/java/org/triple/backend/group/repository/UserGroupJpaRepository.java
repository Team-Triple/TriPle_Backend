package org.triple.backend.group.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.group.entity.userGroup.UserGroup;

public interface UserGroupJpaRepository extends JpaRepository<UserGroup, Long> {
}
