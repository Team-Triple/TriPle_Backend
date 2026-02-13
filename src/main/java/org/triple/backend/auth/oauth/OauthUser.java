package org.triple.backend.auth.oauth;

public record OauthUser(
        OauthProvider provider,
        String providerId,
        String email,
        String nickname,
        String profileUrl
) {
}
