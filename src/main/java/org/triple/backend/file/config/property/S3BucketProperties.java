package org.triple.backend.file.config.property;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.regions.Region;

@Validated
@ConfigurationProperties(prefix = "app.aws.s3")
public record S3BucketProperties(
        @NotNull Region region,
        @NotBlank String bucket,
        @NotNull @Valid S3PresignProperties presign,
        @NotNull @Valid S3PrefixProperties prefix,
        @NotNull @Valid S3UploadPolicyProperties uploadPolicy
) {
    public Region getRegion() {
        return region;
    }

    public String getBucket() {
        return bucket;
    }

    public S3PresignProperties getPresign() {
        return presign;
    }

    public S3PrefixProperties getPrefix() {
        return prefix;
    }

    public S3UploadPolicyProperties getUploadPolicy() {
        return uploadPolicy;
    }
}
