package org.triple.backend.global.config.property;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        @NotEmpty String appMapping,
        @NotEmpty List<@NotBlank String> allowedOrigins,
        @NotEmpty List<@Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|OPTIONS") String> allowedMethods,
        @NotEmpty List<@NotBlank String> allowedHeaders,
        boolean allowCredentials,
        List<String> exposedHeaders
) {
    public String getAppMapping() {
        return appMapping;
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

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    @AssertTrue(message = "allowCredentials=true cannot be used with wildcard origin.")
    public boolean isAllowCredentialsValid() {
        return !allowCredentials || !allowedOrigins.contains("*");
    }
}
