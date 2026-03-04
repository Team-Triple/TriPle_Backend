package org.triple.backend.auth.config.property;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.uuid")
public record SecurityUuidProperties(
        @NotBlank String secret
) {
}
