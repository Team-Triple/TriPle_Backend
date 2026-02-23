package org.triple.backend.file.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.regions.Region;

@Getter
@Validated
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.aws.s3")
public class S3BucketProperties {
    @NotNull
    private final Region region;

    @NotBlank
    private final String bucket;

    @NotNull
    @Valid
    private final S3PresignProperties presign;

    @NotNull
    @Valid
    private final S3PrefixProperties prefix;

    @NotNull
    @Valid
    private final S3UploadPolicyProperties uploadPolicy;
}
