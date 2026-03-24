package org.triple.backend.auth.unit.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.auth.crypto.UserIdentityResolver;
import org.triple.backend.auth.crypto.UuidCrypto;
import org.triple.backend.auth.crypto.UuidToUserIdCache;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserIdentityResolverTest {

    private final UuidCrypto uuidCrypto = mock(UuidCrypto.class);
    private final UuidToUserIdCache uuidToUserIdCache = mock(UuidToUserIdCache.class);
    private final UserIdentityResolver userIdentityResolver = new UserIdentityResolver(uuidCrypto, uuidToUserIdCache);

    @Test
    @DisplayName("resolve returns null for null principal")
    void resolveNullPrincipal() {
        assertThat(userIdentityResolver.resolve(null)).isNull();
    }

    @Test
    @DisplayName("parsePublicUuid returns same uuid when principal is uuid")
    void parsePublicUuidFromUuid() {
        UUID uuid = UUID.randomUUID();

        assertThat(userIdentityResolver.parsePublicUuid(uuid)).isEqualTo(uuid);
    }

    @Test
    @DisplayName("resolve decrypts principal and returns cached user id")
    void resolveFromEncryptedPrincipal() {
        UUID uuid = UUID.randomUUID();
        when(uuidCrypto.decryptToUuid("enc")).thenReturn(uuid);
        when(uuidToUserIdCache.find(uuid)).thenReturn(10L);

        Long resolved = userIdentityResolver.resolve("enc");

        assertThat(resolved).isEqualTo(10L);
        verify(uuidToUserIdCache).find(uuid);
    }

    @Test
    @DisplayName("resolve returns null when uuid parsing fails")
    void resolveReturnsNullWhenParseFails() {
        when(uuidCrypto.decryptToUuid("bad")).thenReturn(null);

        assertThat(userIdentityResolver.resolve("bad")).isNull();
    }
}
