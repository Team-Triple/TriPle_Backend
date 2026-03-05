package org.triple.backend.auth.oauth;

public interface OauthClient {
    OauthProvider provider();
    OauthUser fetchUser(String code);
}
