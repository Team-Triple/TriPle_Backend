package org.triple.backend.file.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.triple.backend.file.dto.request.PresignedUrlRequestDtos;
import org.triple.backend.file.dto.request.UploadedKeysRequestDto;
import org.triple.backend.file.dto.response.FileUploadCompleteResponsesDto;
import org.triple.backend.file.dto.response.PresignedUrlResponseDto;
import org.triple.backend.file.dto.response.PresignedUrlResponsesDto;
import org.triple.backend.file.dto.response.UploadResult;
import org.triple.backend.file.infra.exception.FinalizeUploadException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FileServiceFacade {
    private final FileService fileService;

    public PresignedUrlResponsesDto issuePutPresignedUrls(final PresignedUrlRequestDtos requestDtos, final Long userId) {
        List<PresignedUrlResponseDto> presignedUrlResponseDtos = requestDtos.presignedUrlRequestDtos().stream()
                .map(requestDto -> fileService.issuePutPresignedUrl(requestDto, userId))
                .toList();

        return new PresignedUrlResponsesDto(presignedUrlResponseDtos);
    }

    public FileUploadCompleteResponsesDto completeUploads(final UploadedKeysRequestDto request, final Long userId) {
        List<String> pendingKeys = request.keys();

        List<UploadResult> uploadResults = pendingKeys.stream()
                .map(pendingKey -> finalizeUpload(pendingKey, userId))
                .toList();

        return new FileUploadCompleteResponsesDto(uploadResults);
    }

    private UploadResult finalizeUpload(final String pendingKey, final Long userId) {
        try {
            fileService.validateFinalizeUpload(pendingKey, userId);
            String uploadedKey = fileService.finalizeUpload(pendingKey);
            fileService.saveFile(uploadedKey, userId);
            return UploadResult.success(pendingKey, uploadedKey);
        } catch (FinalizeUploadException e) {
            return UploadResult.fail(pendingKey, e.getHttpStatus(), e.getMessage());
        }
    }
}
