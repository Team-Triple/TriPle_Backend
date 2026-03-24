package org.triple.backend.auth.crypto;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.triple.backend.user.repository.UserJpaRepository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Caffeine Cache를 이용하였음
 * 1. 조회 전략(find) : publicUuid로 O(1) 수준의 검색, 캐시 미스 시 userJpaRepository에서 직접 조회 후 캐시 적재
 * 2. 저장 전략(save) : publicUuid와 userId를 받아 저장. 만약, 캐시 가득 찼을 시 Caffeine 내부 정책에 의해 최저 조회 객체들 삭제 후 저장
 * 3. 삭제 전략(invalidate) : 그냥 publicUuid로 삭제
 */
@Component
@RequiredArgsConstructor
public class UuidToUserIdCache {
    private final UserJpaRepository userJpaRepository;
    private final Cache<UUID, Optional<Long>> cache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofSeconds(10000))
            .build();

    public Long find(UUID publicUuid) {
        return cache.get(publicUuid, userJpaRepository::findIdByPublicUuid)
                .orElse(null);
    }

    public void invalidate(UUID publicUuid) {
        cache.invalidate(publicUuid);
    }

    public void save(UUID publicUuid, Long userId) {
        cache.put(publicUuid, Optional.ofNullable(userId));
    }
}