package org.triple.backend.file.dto.response;

public record PresignedUrlFailedDto(
    String fileName,
    String mimeType,
    Integer errorCode,
    String message
) implements PresignedUrlResponse {

    public static PresignedUrlResponse of(String fileName, String mimeType, Integer errorCode, String message) {
        return new PresignedUrlFailedDto(fileName, mimeType, errorCode, message);
    }
}
