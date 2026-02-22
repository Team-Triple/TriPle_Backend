package org.triple.backend.file.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.file.dto.request.PresignedUrlRequestDtos;
import org.triple.backend.file.dto.request.UploadedKeysRequestDto;
import org.triple.backend.file.dto.response.FileUploadCompleteResponsesDto;
import org.triple.backend.file.dto.response.PresignedUrlResponsesDto;
import org.triple.backend.file.service.FileServiceFacade;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {
    private final FileServiceFacade fileServiceFacade;

    @LoginRequired
    @PostMapping("/upload-presign")
    public PresignedUrlResponsesDto issuePutPresignedUrls(
            @Valid @RequestBody PresignedUrlRequestDtos requestDto,
            @LoginUser Long userId
    ) {
        return fileServiceFacade.issuePutPresignedUrls(requestDto, userId);
    }

    @LoginRequired
    @PostMapping("/upload-complete")
    public FileUploadCompleteResponsesDto completeUpload(
            @Valid @RequestBody UploadedKeysRequestDto requestDto,
            @LoginUser Long userId
    ) {
        return fileServiceFacade.completeUploads(requestDto, userId);
    }
}
