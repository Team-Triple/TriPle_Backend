package org.triple.backend.auth.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "server.servlet.session.cookie")
public record CookieProperties(
        String path,
        String sameSite,
        boolean secure,
        boolean httpOnly
) {}
