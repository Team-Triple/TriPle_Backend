package org.triple.backend.common.annotation;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.ActiveProfiles;
import org.triple.backend.auth.config.property.CookieProperties;
import org.triple.backend.auth.cookie.CookieManager;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.auth.session.UserIdentityResolver;
import org.triple.backend.auth.session.UuidCrypto;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(RestDocumentationExtension.class)
@Import({SessionManager.class, UserIdentityResolver.class, UuidCrypto.class, CookieManager.class})
@EnableConfigurationProperties(CookieProperties.class)
@WebMvcTest
@ActiveProfiles("test")
public @interface WebMvcDocsTest {
}
