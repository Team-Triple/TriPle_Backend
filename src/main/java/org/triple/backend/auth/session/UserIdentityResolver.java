package org.triple.backend.auth.session;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class UserIdentityResolver {

    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;

    private final ObjectProvider<UserJpaRepository> userJpaRepositoryProvider;
    private final UuidCrypto uuidCrypto;
    private final ConcurrentHashMap<UUID, CachedUserId> uuidToUserIdCache = new ConcurrentHashMap<>();

    public @Nullable Long resolve(@Nullable Object principal) {
        if (principal == null) {
            return null;
        }

        if (principal instanceof Long userId) {
            return userId;
        }

        UUID publicUuid = parsePublicUuid(principal);
        if (publicUuid == null) {
            return null;
        }

        return findUserIdByPublicUuid(publicUuid);
    }

    private @Nullable UUID parsePublicUuid(Object principal) {
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        return uuidCrypto.decryptToUuid(principal);
    }

    private @Nullable Long findUserIdByPublicUuid(UUID publicUuid) {
        CachedUserId cachedUserId = uuidToUserIdCache.compute(publicUuid, (key, cached) -> {
           long now = System.currentTimeMillis();
            if(cached != null && cached.expiresAt() > now) {
                return cached;
            }

            UserJpaRepository userJpaRepository = userJpaRepositoryProvider.getIfAvailable();
            if(userJpaRepository == null) {
                return null;
            }

            return userJpaRepository.findIdByPublicUuid(key)
                    .map(id -> new CachedUserId(id, now + CACHE_TTL_MILLIS))
                    .orElse(null);
        });

        return cachedUserId == null ? null : cachedUserId.userId();
    }
}
