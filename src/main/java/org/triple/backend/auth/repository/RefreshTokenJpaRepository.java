package org.triple.backend.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.auth.entity.RefreshToken;

import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findTopByUserIdOrderByIdDesc(Long userId);
    void deleteByUserId(Long userId);
}
