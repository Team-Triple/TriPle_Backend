package org.triple.backend.common.annotation;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.triple.backend.auth.jwt.JwtAuthenticationInterceptor;
import org.triple.backend.auth.jwt.JwtUserArgumentResolver;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(RestDocumentationExtension.class)
@Import({JwtAuthenticationInterceptor.class, JwtUserArgumentResolver.class})
@WebMvcTest
@ActiveProfiles("test")
public @interface WebMvcDocsTest {
}
