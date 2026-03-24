package org.triple.backend.auth.crypto;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserIdentityResolver {
    private final UuidCrypto uuidCrypto;
    private final UuidToUserIdCache uuidToUserIdCache;

    public @Nullable Long resolve(@Nullable Object principal) {
        if (principal == null) return null;

        UUID publicUuid = parsePublicUuid(principal);
        if (publicUuid == null) return null;

        return uuidToUserIdCache.find(publicUuid);
    }

    public @Nullable UUID parsePublicUuid(Object principal) {
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        return uuidCrypto.decryptToUuid(principal);
    }
}
