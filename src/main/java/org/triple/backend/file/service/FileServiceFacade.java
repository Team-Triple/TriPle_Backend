package org.triple.backend.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.triple.backend.file.dto.request.PresignedUrlRequestDtos;
import org.triple.backend.file.dto.request.UploadedKeysRequestDto;
import org.triple.backend.file.dto.response.FileUploadCompleteResponsesDto;
import org.triple.backend.file.dto.response.PresignedUrlResponse;
import org.triple.backend.file.dto.response.PresignedUrlResponsesDto;
import org.triple.backend.file.dto.response.UploadFailed;
import org.triple.backend.file.dto.response.UploadResult;
import org.triple.backend.file.dto.response.UploadSuccess;
import org.triple.backend.file.infra.exception.FinalizeUploadException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceFacade {
    private final FileService fileService;

    public PresignedUrlResponsesDto issuePutPresignedUrls(final PresignedUrlRequestDtos requestDtos, final Long userId) {
        List<PresignedUrlResponse> presignedUrlResponses = requestDtos.presignedUrlRequestDtos().stream()
                .map(requestDto -> fileService.issuePutPresignedUrl(requestDto, userId))
                .toList();

        return new PresignedUrlResponsesDto(presignedUrlResponses);
    }

    public FileUploadCompleteResponsesDto completeUploads(final UploadedKeysRequestDto request, final Long userId) {
        List<String> pendingKeys = request.keys();

        List<UploadResult> uploadResults = pendingKeys.stream()
                .map(pendingKey -> finalizeUpload(pendingKey, userId))
                .toList();

        return new FileUploadCompleteResponsesDto(uploadResults);
    }

    private UploadResult finalizeUpload(final String pendingKey, final Long userId) {
        log.debug("요청받은 업로드 검증 위한 pendingKey = {}", pendingKey);
        try {
            fileService.validateFinalizeUpload(pendingKey, userId);
            String uploadedKey = fileService.finalizeUpload(pendingKey);
            String uploadedUrl = fileService.concatPrefix(uploadedKey);
            fileService.saveFile(uploadedKey, uploadedUrl, userId);
            return new UploadSuccess(pendingKey, uploadedKey, uploadedUrl);
        } catch (FinalizeUploadException e) {
            return new UploadFailed(pendingKey, e.getHttpStatus(), e.getMessage());
        }
    }
}
