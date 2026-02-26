package org.triple.backend.file.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.triple.backend.file.dto.request.PresignedUrlRequestDto;
import org.triple.backend.file.dto.response.PresignedUrlFailedDto;
import org.triple.backend.file.dto.response.PresignedUrlResponse;
import org.triple.backend.file.dto.response.PresignedUrlSuccessDto;
import org.triple.backend.file.entity.File;
import org.triple.backend.file.infra.BucketKeyPublisher;
import org.triple.backend.file.infra.PresignedUrl;
import org.triple.backend.file.infra.S3Bucket;
import org.triple.backend.file.infra.exception.FinalizeUploadException;
import org.triple.backend.file.infra.exception.InvalidKeyException;
import org.triple.backend.file.repository.FileJpaRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    private final S3Bucket s3Bucket;
    private final BucketKeyPublisher bucketKeyPublisher;
    private final FileJpaRepository fileJpaRepository;

    public PresignedUrlResponse issuePutPresignedUrl(PresignedUrlRequestDto requestDto, Long userId) {
        log.debug("request fileName={}, mimeType={}", requestDto.fileName(), requestDto.mimeType());
        try {
            s3Bucket.validateContentType(requestDto.mimeType());
            String pendingKey = bucketKeyPublisher.publishPendingKey(requestDto.fileName(), userId);
            PresignedUrl presignedUrl = s3Bucket.issuePresignedUrl(pendingKey, requestDto.mimeType());
            return PresignedUrlSuccessDto.of(requestDto, presignedUrl);
        } catch (RuntimeException e) {
            log.warn("failed to issue presigned url. request={}", requestDto, e);
            return PresignedUrlFailedDto.of(
                    requestDto.fileName(),
                    requestDto.mimeType(),
                    resolveHttpStatus(e),
                    resolveMessage(e)
            );
        }
    }

    public void validateFinalizeUpload(final String pendingKey, final Long userId) {
        try {
            s3Bucket.validatePendingKey(pendingKey, userId);
            s3Bucket.validateUploadedObject(pendingKey);
        } catch (FinalizeUploadException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FinalizeUploadException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to validate upload completion.",
                    e
            );
        }
    }

    public String finalizeUpload(final String pendingKey) {
        try {
            String uploadedKey = bucketKeyPublisher.publishUploadedKey(pendingKey);
            s3Bucket.copyObject(pendingKey, uploadedKey);
            s3Bucket.deleteObject(pendingKey);
            return uploadedKey;
        } catch (FinalizeUploadException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new InvalidKeyException(e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new FinalizeUploadException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to finalize upload.",
                    e
            );
        }
    }

    @Transactional
    public void saveFile(String uploadedKey, String uploadedUrl, Long userId) {
        try {
            File file = File.of(userId, uploadedUrl);
            fileJpaRepository.save(file);
            fileJpaRepository.flush();
            log.debug("saved file metadata uploadedUrl={}", uploadedUrl);
        } catch (IllegalArgumentException e) {
            throw new InvalidKeyException(e.getMessage(), e);
        } catch (RuntimeException e) {
            deleteUploadedObject(uploadedKey, e);
            throw new FinalizeUploadException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to save file metadata.",
                    e
            );
        }
    }

    public String concatPrefix(String uploadedKey) {
        return s3Bucket.concatUploadPrefix(uploadedKey);
    }

    private void deleteUploadedObject(String uploadedKey, RuntimeException originalException) {
        try {
            s3Bucket.deleteObject(uploadedKey);
        } catch (RuntimeException e) {
            log.error("failed to compensate uploaded object deletion. uploadedKey={}", uploadedKey, e);
            originalException.addSuppressed(e);
        }
    }

    private Integer resolveHttpStatus(RuntimeException e) {
        if (e instanceof FinalizeUploadException finalizeUploadException) {
            return finalizeUploadException.getHttpStatus().value();
        }
        if (e instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST.value();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private String resolveMessage(RuntimeException e) {
        return e.getMessage() == null ? "Request processing failed." : e.getMessage();
    }
}
