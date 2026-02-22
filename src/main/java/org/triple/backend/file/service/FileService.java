package org.triple.backend.file.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.triple.backend.file.dto.request.PresignedUrlRequestDto;
import org.triple.backend.file.dto.response.PresignedUrlResponseDto;
import org.triple.backend.file.entity.File;
import org.triple.backend.file.infra.BucketKeyPublisher;
import org.triple.backend.file.infra.PresignedUrl;
import org.triple.backend.file.infra.S3Bucket;
import org.triple.backend.file.infra.exception.FinalizeUploadException;
import org.triple.backend.file.infra.exception.InvalidKeyException;
import org.triple.backend.file.repository.FileJpaRepository;

/**
 * DB, S3Bucket 에서 발생할 수 있는 예외들을 처리
 * 디테일한 예외의 경계는 여기까지이다.
 * 모든 예외를 추상화 단계가 가장 높은 FinalizeUploadException으로 전환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    private final S3Bucket s3Bucket;
    private final BucketKeyPublisher bucketKeyPublisher;
    private final FileJpaRepository fileJpaRepository;

    public PresignedUrlResponseDto issuePutPresignedUrl(PresignedUrlRequestDto requestDto, Long userId) {
        try {
            s3Bucket.validateContentType(requestDto.mimeType());
            String pendingKey = bucketKeyPublisher.publishPendingKey(requestDto.fileName(), userId);
            PresignedUrl presignedUrl = s3Bucket.issuePresignedUrl(pendingKey, requestDto.mimeType());
            return PresignedUrlResponseDto.success(requestDto, presignedUrl);
        } catch (RuntimeException e) {
            log.warn("Presigned URL 발급에 실패했습니다. request={}", requestDto, e);
            return PresignedUrlResponseDto.fail(requestDto, resolveHttpStatus(e), resolveMessage(e));
        }
    }

    public void validateFinalizeUpload(final String pendingKey, final Long userId) {
        try {
            s3Bucket.validatePendingKey(pendingKey, userId);
            s3Bucket.validateUploadedObject(pendingKey);
        } catch (FinalizeUploadException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FinalizeUploadException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "업로드 완료 검증에 실패했습니다.",
                    e);
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
            throw new FinalizeUploadException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "업로드 완료 처리에 실패했습니다.",
                    e);
        }
    }

    @Transactional
    public void saveFile(String uploadedKey, Long userId) {
        try {
            File file = File.of(userId, uploadedKey);
            fileJpaRepository.save(file);
        } catch (IllegalArgumentException e) {
            throw new InvalidKeyException(e.getMessage(), e);
        } catch (RuntimeException e) {
            deleteUploadedObject(uploadedKey, e);   //보상
            throw new FinalizeUploadException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "파일 메타데이터 DB 저장에 실패했습니다.",
                    e);
        }
    }

    private void deleteUploadedObject(String uploadedKey, RuntimeException originalException) {
        try {
            s3Bucket.deleteObject(uploadedKey);
        } catch (RuntimeException e) {
            log.error("DB 저장 실패 후 S3 파일 삭제에 실패했습니다. uploadedKey={}", uploadedKey, e);
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
        return e.getMessage() == null ? "요청 처리에 실패했습니다." : e.getMessage();
    }
}
