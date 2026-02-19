package org.triple.backend.group.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.group.entity.joinApply.JoinApply;

public interface JoinJpaApplyRepository extends JpaRepository<JoinApply, Long> {
}
