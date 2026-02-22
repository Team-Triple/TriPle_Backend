package org.triple.backend.file.dto.response;

import java.util.List;

public record FileUploadCompleteResponsesDto(
        List<UploadResult> uploadResults
) {
}
