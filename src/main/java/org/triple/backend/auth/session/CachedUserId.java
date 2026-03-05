package org.triple.backend.auth.session;

public record CachedUserId(Long userId, long expiresAt) {
}
