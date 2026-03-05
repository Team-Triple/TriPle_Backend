package org.triple.backend.auth.config.property;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rest-client")
public record RestClientProperties(
        @Min(1) int connectTimeout,
        @Min(1) int readTimeout
) {
}
