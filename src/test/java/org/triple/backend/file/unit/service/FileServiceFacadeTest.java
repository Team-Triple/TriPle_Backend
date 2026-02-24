package org.triple.backend.file.unit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.triple.backend.file.dto.request.PresignedUrlRequestDto;
import org.triple.backend.file.dto.request.PresignedUrlRequestDtos;
import org.triple.backend.file.dto.request.UploadedKeysRequestDto;
import org.triple.backend.file.dto.response.FileUploadCompleteResponsesDto;
import org.triple.backend.file.dto.response.PresignedUrlResponseDto;
import org.triple.backend.file.dto.response.PresignedUrlResponsesDto;
import org.triple.backend.file.dto.response.UploadResult;
import org.triple.backend.file.infra.exception.FinalizeUploadException;
import org.triple.backend.file.service.FileService;
import org.triple.backend.file.service.FileServiceFacade;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileServiceFacadeTest {

    @Mock
    private FileService fileService;

    @InjectMocks
    private FileServiceFacade fileServiceFacade;

    @Test
    @DisplayName("Presigned URL 발급 요청 목록을 받으면 요청 순서대로 응답 목록을 반환한다.")
    void Presigned_URL_발급_요청_목록을_받으면_요청_순서대로_응답_목록을_반환한다() {
        // given
        Long userId = 1L;

        PresignedUrlRequestDto firstRequest = new PresignedUrlRequestDto("a.jpg", "image/jpeg");
        PresignedUrlRequestDto secondRequest = new PresignedUrlRequestDto("b.png", "image/png");
        PresignedUrlRequestDtos requestDtos = new PresignedUrlRequestDtos(List.of(firstRequest, secondRequest));

        PresignedUrlResponseDto firstResponse = new PresignedUrlResponseDto(
                "a.jpg",
                "image/jpeg",
                "uploads/pending/1/a.jpg",
                "https://example.com/a",
                Instant.parse("2030-01-01T00:00:00Z"),
                true,
                null,
                null
        );
        PresignedUrlResponseDto secondResponse = new PresignedUrlResponseDto(
                "b.png",
                "image/png",
                null,
                null,
                null,
                false,
                400,
                "invalid mimeType"
        );

        given(fileService.issuePutPresignedUrl(firstRequest, userId)).willReturn(firstResponse);
        given(fileService.issuePutPresignedUrl(secondRequest, userId)).willReturn(secondResponse);

        // when
        PresignedUrlResponsesDto result = fileServiceFacade.issuePutPresignedUrls(requestDtos, userId);

        // then
        assertThat(result.presignedUrlResponseDtos()).containsExactly(firstResponse, secondResponse);
    }

    @Test
    @DisplayName("업로드 완료는 키별로 성공/실패를 누적해 모두 반환한다.")
    void 업로드_완료는_키별로_성공_실패를_누적해_모두_반환한다() {
        // given
        Long userId = 1L;
        String firstPendingKey = "uploads/pending/1/a.jpg";
        String secondPendingKey = "uploads/pending/1/b.jpg";
        String firstUploadedKey = "uploads/uploaded/1/a.jpg";
        String firstUploadedUrl = "https://triple-dev-s3.s3.ap-northeast-2.amazonaws.com/uploads/uploaded/1/a.jpg";

        UploadedKeysRequestDto requestDto = new UploadedKeysRequestDto(List.of(firstPendingKey, secondPendingKey));

        doNothing().when(fileService).validateFinalizeUpload(firstPendingKey, userId);
        doThrow(new FinalizeUploadException(HttpStatus.NOT_FOUND, "업로드된 파일을 찾을 수 없습니다."))
                .when(fileService).validateFinalizeUpload(secondPendingKey, userId);

        given(fileService.finalizeUpload(firstPendingKey)).willReturn(firstUploadedKey);
        doNothing().when(fileService).saveFile(firstUploadedKey, userId);
        given(fileService.concatPrefix(firstUploadedKey)).willReturn(firstUploadedUrl);

        // when
        FileUploadCompleteResponsesDto result = fileServiceFacade.completeUploads(requestDto, userId);

        // then
        List<UploadResult> uploadResults = result.uploadResults();
        assertThat(uploadResults).hasSize(2);

        UploadResult first = uploadResults.get(0);
        assertThat(first.pendingKey()).isEqualTo(firstPendingKey);
        assertThat(first.uploadedKey()).isEqualTo(firstUploadedKey);
        assertThat(first.uploadedUrl()).isEqualTo(firstUploadedUrl);
        assertThat(first.success()).isTrue();

        UploadResult second = uploadResults.get(1);
        assertThat(second.pendingKey()).isEqualTo(secondPendingKey);
        assertThat(second.uploadedKey()).isNull();
        assertThat(second.uploadedUrl()).isNull();
        assertThat(second.success()).isFalse();
        assertThat(second.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(second.message()).isEqualTo("업로드된 파일을 찾을 수 없습니다.");

        verify(fileService, never()).finalizeUpload(eq(secondPendingKey));
        verify(fileService, never()).saveFile(eq("uploads/uploaded/1/b.jpg"), eq(userId));
    }
}
