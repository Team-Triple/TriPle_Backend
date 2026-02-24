package org.triple.backend.file.dto.response;

import java.time.Instant;
import org.triple.backend.file.dto.request.PresignedUrlRequestDto;
import org.triple.backend.file.infra.PresignedUrl;

public record PresignedUrlSuccessDto(
    String fileName,
    String mimeType,
    String key,
    String presignedUrl,
    Instant expiresAt
) implements PresignedUrlResponse {
    public static PresignedUrlResponse of(PresignedUrlRequestDto requestDto, PresignedUrl presignedUrl) {
        return new PresignedUrlSuccessDto(
            requestDto.fileName(),
            requestDto.mimeType(),
            presignedUrl.key(),
            presignedUrl.presignedUrl(),
            presignedUrl.expiresAt());
    }
}
