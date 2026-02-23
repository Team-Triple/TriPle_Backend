package org.triple.backend.file.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(S3BucketProperties.class)
public class S3Config {
    private final S3BucketProperties s3BucketProperties;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(s3BucketProperties.getRegion())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(s3BucketProperties.getRegion())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
