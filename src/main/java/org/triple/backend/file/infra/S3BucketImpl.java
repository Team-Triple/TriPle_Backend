package org.triple.backend.file.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.triple.backend.file.config.property.S3BucketProperties;
import org.triple.backend.file.infra.exception.CopyFailedException;
import org.triple.backend.file.infra.exception.DeleteFailedException;
import org.triple.backend.file.infra.exception.InvalidKeyException;
import org.triple.backend.file.infra.exception.UploadFailedException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * S3Bucket과 직접적으로 통신하는 객체
 * infra 레이어(외부 API)이기 때문에 retry 정책과 세세한 예외 처리 책임
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3BucketImpl implements S3Bucket {
    private final S3BucketProperties s3BucketProp;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Override
    public void validateContentType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            throw new InvalidKeyException("mimeType은 null이거나 공백일 수 없습니다.");
        }
        if (!s3BucketProp.uploadPolicy().allowedContentTypes().contains(mimeType)) {
            throw new InvalidKeyException("허용되지 않는 mimeType입니다: " + mimeType);
        }
    }

    @Override
    public PresignedUrl issuePresignedUrl(String pendingKey, String mimeType) {
        PutObjectPresignRequest putObjectPresignRequest = makePutObjectPresignedRequest(pendingKey, mimeType);
        PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(putObjectPresignRequest);
        return new PresignedUrl(pendingKey, presignedPutObjectRequest);
    }

    @Override
    public void validatePendingKey(String pendingKey, Long userId) {
        if (pendingKey == null || pendingKey.isBlank()) {
            throw new InvalidKeyException("pendingKey는 null이거나 공백일 수 없습니다.");
        }
        if (!pendingKey.startsWith(s3BucketProp.prefix().pending() + userId + "/")) {
            throw new InvalidKeyException("pendingKey의 prefix 형식이 올바르지 않습니다.");
        }
    }

    @Retryable(
            retryFor = {S3Exception.class, SdkClientException.class},
            noRetryFor = {UploadFailedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    @Override
    public void validateUploadedObject(String pendingKey) {
        try {
            s3Client.headObject(makeHeadObjectRequest(pendingKey));
        } catch (NoSuchKeyException e) {
            throw new UploadFailedException(HttpStatus.NOT_FOUND, "업로드된 파일을 찾을 수 없습니다: " + pendingKey, e);
        } catch (S3Exception e) {
            if (!isRetryable(e)) {
                throw new UploadFailedException(
                        toHttpStatus(e.statusCode(), HttpStatus.BAD_REQUEST),
                        "업로드 상태를 확인할 수 없습니다: " + pendingKey,
                        e
                );
            }
            throw e;
        }
    }

    @Retryable(
            retryFor = {S3Exception.class, SdkClientException.class},
            noRetryFor = {CopyFailedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    @Override
    public void copyObject(String sourceKey, String destinationKey) {
        log.debug("업로드 할 sourceKey = {}, destinationKey = {}", sourceKey, destinationKey);
        try {
            s3Client.copyObject(
                    CopyObjectRequest.builder()
                            .sourceBucket(s3BucketProp.bucket())
                            .sourceKey(sourceKey)
                            .destinationBucket(s3BucketProp.bucket())
                            .destinationKey(destinationKey)
                            .build()
            );
        } catch (S3Exception e) {
            if (!isRetryable(e)) {
                throw new CopyFailedException(
                        toHttpStatus(e.statusCode(), HttpStatus.BAD_GATEWAY),
                        "파일 복사에 실패했습니다: " + sourceKey,
                        e
                );
            }
            throw e;
        }
    }

    @Retryable(
            retryFor = {S3Exception.class, SdkClientException.class},
            noRetryFor = {DeleteFailedException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    @Override
    public void deleteObject(String sourceKey) {
        log.debug("삭제 할 sourceKey = {}", sourceKey);
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(s3BucketProp.bucket())
                            .key(sourceKey)
                            .build()
            );
        } catch (S3Exception e) {
            if (!isRetryable(e)) {
                throw new DeleteFailedException(
                        toHttpStatus(e.statusCode(), HttpStatus.BAD_GATEWAY),
                        "파일 삭제에 실패했습니다: " + sourceKey,
                        e
                );
            }
            throw e;
        }
    }

    @Override
    public String concatUploadPrefix(String uploadedKey) {
        return s3BucketProp.prefix().getUrlPrefix() + uploadedKey;
    }

    private PutObjectPresignRequest makePutObjectPresignedRequest(String key, String mimeType) {
        PutObjectRequest putObjectRequest = makePutObjectRequest(key, mimeType);
        return makePutObjectPresignRequest(putObjectRequest);
    }

    private PutObjectPresignRequest makePutObjectPresignRequest(PutObjectRequest putObjectRequest) {
        return PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(s3BucketProp.presign().putExpireSeconds()))
                .putObjectRequest(putObjectRequest)
                .build();
    }

    private PutObjectRequest makePutObjectRequest(String key, String mimeType) {
        return PutObjectRequest.builder()
                .bucket(s3BucketProp.bucket())
                .key(key)
                .contentType(mimeType)
                .build();
    }

    private HeadObjectRequest makeHeadObjectRequest(String key) {
        return HeadObjectRequest.builder()
                .bucket(s3BucketProp.bucket())
                .key(key)
                .build();
    }

    private boolean isRetryable(S3Exception e) {    //429: 넘 많은 요청 500: internerServer
        int statusCode = e.statusCode();
        return statusCode == 429 || statusCode >= 500;
    }

    private HttpStatus toHttpStatus(int statusCode, HttpStatus fallbackStatus) {
        try {
            return HttpStatus.valueOf(statusCode);
        } catch (RuntimeException e) {
            return fallbackStatus;
        }
    }

    @Recover
    public void recoverUploadFailed(S3Exception e, String pendingKey) {
        log.error("headObject 재시도 횟수를 초과했습니다. key={}", pendingKey, e);
        throw new UploadFailedException(HttpStatus.BAD_GATEWAY,
                "재시도 후에도 업로드 검증에 실패했습니다: " + pendingKey,
                e);
    }

    @Recover
    public void recoverUploadFailed(SdkClientException e, String pendingKey) {
        log.error("headObject 재시도 횟수를 초과했습니다. key={}", pendingKey, e);
        throw new UploadFailedException(HttpStatus.BAD_GATEWAY,
                "재시도 후에도 업로드 검증에 실패했습니다: " + pendingKey,
                e);
    }

    @Recover
    public void recoverCopyFailed(S3Exception e, String sourceKey, String destinationKey) {
        log.error("copyObject 재시도 횟수를 초과했습니다. source={} destination={}", sourceKey, destinationKey, e);
        throw new CopyFailedException(HttpStatus.BAD_GATEWAY,
                "재시도 후에도 파일 복사에 실패했습니다: " + sourceKey,
                e);
    }

    @Recover
    public void recoverCopyFailed(SdkClientException e, String sourceKey, String destinationKey) {
        log.error("copyObject 재시도 횟수를 초과했습니다. source={} destination={}", sourceKey, destinationKey, e);
        throw new CopyFailedException(HttpStatus.BAD_GATEWAY,
                "재시도 후에도 파일 복사에 실패했습니다: " + sourceKey,
                e);
    }

    @Recover
    public void recoverDeleteFailed(S3Exception e, String sourceKey) {
        log.error("deleteObject 재시도 횟수를 초과했습니다. source={}", sourceKey, e);
        throw new DeleteFailedException(HttpStatus.BAD_GATEWAY,
                "재시도 후에도 파일 삭제에 실패했습니다: " + sourceKey,
                e);
    }

    @Recover
    public void recoverDeleteFailed(SdkClientException e, String sourceKey) {
        log.error("deleteObject 재시도 횟수를 초과했습니다. source={}", sourceKey, e);
        throw new DeleteFailedException(HttpStatus.BAD_GATEWAY,
                "재시도 후에도 파일 삭제에 실패했습니다: " + sourceKey,
                e);
    }
}
