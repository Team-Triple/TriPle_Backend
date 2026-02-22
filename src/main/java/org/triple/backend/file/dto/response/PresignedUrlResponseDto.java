package org.triple.backend.file.dto.response;

import org.triple.backend.file.dto.request.PresignedUrlRequestDto;
import org.triple.backend.file.infra.PresignedUrl;

import java.time.Instant;

public record PresignedUrlResponseDto(
        String fileName,
        String mimeType,
        String key,
        String presignedUrl,
        Instant expiresAt,
        boolean success,
        Integer errorCode,
        String message
) {
    public static PresignedUrlResponseDto success(
            PresignedUrlRequestDto requestDto,
            PresignedUrl presignedUrl
    ) {
        return new PresignedUrlResponseDto(
                requestDto.fileName(),
                requestDto.mimeType(),
                presignedUrl.key(),
                presignedUrl.presignedUrl(),
                presignedUrl.expiresAt(),
                true,
                null,
                null
        );
    }

    public static PresignedUrlResponseDto fail(
            PresignedUrlRequestDto requestDto,
            Integer errorCode,
            String message
    ) {
        return new PresignedUrlResponseDto(
                requestDto.fileName(),
                requestDto.mimeType(),
                null,
                null,
                null,
                false,
                errorCode,
                message
        );
    }
}
