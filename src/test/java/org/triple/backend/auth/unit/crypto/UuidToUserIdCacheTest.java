package org.triple.backend.auth.unit.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.auth.crypto.UuidToUserIdCache;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UuidToUserIdCacheTest {

    private final UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
    private final UuidToUserIdCache cache = new UuidToUserIdCache(userJpaRepository);

    @Test
    @DisplayName("find loads from repository on cache miss and reuses cache on second call")
    void findCachesRepositoryResult() {
        UUID uuid = UUID.randomUUID();
        when(userJpaRepository.findIdByPublicUuid(uuid)).thenReturn(Optional.of(7L));

        Long first = cache.find(uuid);
        Long second = cache.find(uuid);

        assertThat(first).isEqualTo(7L);
        assertThat(second).isEqualTo(7L);
        verify(userJpaRepository, times(1)).findIdByPublicUuid(uuid);
    }

    @Test
    @DisplayName("save puts value into cache without repository lookup")
    void savePutsValue() {
        UUID uuid = UUID.randomUUID();

        cache.save(uuid, 9L);
        Long found = cache.find(uuid);

        assertThat(found).isEqualTo(9L);
        verify(userJpaRepository, times(0)).findIdByPublicUuid(uuid);
    }

    @Test
    @DisplayName("invalidate clears cached value and forces repository fallback")
    void invalidateForcesReload() {
        UUID uuid = UUID.randomUUID();
        cache.save(uuid, 11L);
        when(userJpaRepository.findIdByPublicUuid(uuid)).thenReturn(Optional.of(12L));

        cache.invalidate(uuid);
        Long reloaded = cache.find(uuid);

        assertThat(reloaded).isEqualTo(12L);
        verify(userJpaRepository, times(1)).findIdByPublicUuid(uuid);
    }

    @Test
    @DisplayName("save accepts null and find returns null")
    void saveNull() {
        UUID uuid = UUID.randomUUID();

        cache.save(uuid, null);
        Long found = cache.find(uuid);

        assertThat(found).isNull();
        verify(userJpaRepository, times(0)).findIdByPublicUuid(uuid);
    }
}
