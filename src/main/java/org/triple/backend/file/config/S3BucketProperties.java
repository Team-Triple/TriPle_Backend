package org.triple.backend.file.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Validated
@ConfigurationProperties(prefix = "app.aws.s3")
public class S3BucketProperties {
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

    public S3BucketProperties(
            String bucket,
            S3PresignProperties presign,
            S3PrefixProperties prefix,
            S3UploadPolicyProperties uploadPolicy
    ) {
        this.bucket = bucket;
        this.presign = presign;
        this.prefix = prefix;
        this.uploadPolicy = uploadPolicy;
    }
}
