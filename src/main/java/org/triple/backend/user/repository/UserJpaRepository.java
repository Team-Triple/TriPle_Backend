package org.triple.backend.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.user.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<User,Long> {

    Optional<User> findByProviderAndProviderId(OauthProvider provider, String providerId);

    @Query("SELECT u.id FROM User u where u.publicUuid = :publicUuid")
    Optional<Long> findIdByPublicUuid(UUID publicUuid);

    boolean existsById(Long userId);
}
