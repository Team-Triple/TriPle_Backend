package org.triple.backend.global.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Validated
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {
    @NotEmpty
    private final String appMapping;
    @NotEmpty
    private final List<@NotBlank String> allowedOrigins;
    @NotEmpty
    private final List<@Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|OPTIONS") String> allowedMethods;
    @NotEmpty
    private final List<@NotBlank String> allowedHeaders;
    private final boolean allowCredentials;
    private final List<String> exposedHeaders;

    public CorsProperties(
            String appMapping,
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            boolean allowCredentials,
            List<String> exposedHeaders
    ) {
        this.appMapping = appMapping;
        this.allowedOrigins = allowedOrigins;
        this.allowedMethods = allowedMethods;
        this.allowedHeaders = allowedHeaders;
        this.allowCredentials = allowCredentials;
        this.exposedHeaders = exposedHeaders;
    }

    public String[] getAllowedOrigins() {
        return allowedOrigins.toArray(new String[0]);
    }

    public String[] getAllowedMethods() {
        return allowedMethods.toArray(new String[0]);
    }

    public String[] getAllowedHeaders() {
        return allowedHeaders.toArray(new String[0]);
    }

    public String[] getExposedHeaders() {
        return exposedHeaders.toArray(new String[0]);
    }

    @AssertTrue(message = "allowCredentials=true 일 때 allowedOrigins에 * 를 사용할 수 없습니다.")
    public boolean isAllowCredentialsValid() {
        return !allowCredentials || !allowedOrigins.contains("*");
    }
}
