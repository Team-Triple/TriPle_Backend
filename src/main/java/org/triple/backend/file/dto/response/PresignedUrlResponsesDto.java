package org.triple.backend.file.dto.response;

import java.util.List;

public record PresignedUrlResponsesDto(
        List<PresignedUrlResponse> presignedUrlResponses
) {
}
