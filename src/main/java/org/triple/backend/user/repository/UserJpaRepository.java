package org.triple.backend.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.user.entity.User;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User,Long> {

    Optional<User> findByProviderAndProviderId(OauthProvider provider, String providerId);
}
