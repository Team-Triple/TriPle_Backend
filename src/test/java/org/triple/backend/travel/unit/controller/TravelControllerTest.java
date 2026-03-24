package org.triple.backend.travel.unit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.travel.controller.TravelItineraryController;
import org.triple.backend.travel.dto.request.TravelItinerarySaveRequestDto;
import org.triple.backend.travel.dto.response.TravelItineraryCursorResponseDto;
import org.triple.backend.travel.dto.response.TravelItineraryInfoResponseDto;
import org.triple.backend.travel.dto.response.TravelItinerarySaveResponseDto;
import org.triple.backend.travel.entity.UserRole;
import org.triple.backend.travel.exception.TravelItineraryErrorCode;
import org.triple.backend.travel.exception.UserTravelItineraryErrorCode;
import org.triple.backend.travel.service.TravelItineraryService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TravelItineraryController.class)
class TravelControllerTest extends ControllerTest {

    @MockitoBean
    private TravelItineraryService travelItineraryService;

    @MockitoBean
    private SessionManager sessionManager;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @Test
    @DisplayName("요청 시 TravelSaveResponseDto를 반환해야함")
    void 요청_반환() throws Exception {
        //given
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);

        TravelItinerarySaveResponseDto response = new TravelItinerarySaveResponseDto(1L);
        given(travelItineraryService.saveTravels(any(TravelItinerarySaveRequestDto.class), any()))
                .willReturn(response);

        String requestBody = buildTravelSaveRequestBody(
                "제목", "2026-02-15T00:00", "2026-02-18T00:00", 1L, "설명"
        );

        //when, then
        mockMvc.perform(post("/travels")
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itineraryId").value(1L))
                .andDo(
                        document("travels/create",
                                requestFields(
                                        fieldWithPath("title").description("여행 제목 (필수)"),
                                        fieldWithPath("startAt").description("시작 일시 (yyyy-MM-dd'T'HH:mm 형식, 필수)"),
                                        fieldWithPath("endAt").description("종료 일시 (yyyy-MM-dd'T'HH:mm 형식, 필수)"),
                                        fieldWithPath("groupId").description("그룹 ID (필수)"),
                                        fieldWithPath("description").description("여행 설명 (100글자)").optional(),
                                        fieldWithPath("memberUuids").description("member UUID list (optional)").optional()
                                ),
                                responseFields(
                                        fieldWithPath("itineraryId").description("여행 일정 ID")
                                )
                        )
                );
    }

    @Test
    @DisplayName("비로그인 사용자가 여행 일정 생성 요청 시 401을 반환한다.")
    void 비로그인_사용자가_여행_일정_생성_요청_시_401을_반환한다() throws Exception {
        given(sessionManager.getUserIdOrThrow(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

        String requestBody = buildTravelSaveRequestBody(
                "제목", "2026-02-15T00:00", "2026-02-18T00:00", 1L, "설명"
        );

        mockMvc.perform(post("/travels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("travels/create-fail-unauthorized",
                        requestFields(
                                fieldWithPath("title").description("여행 제목 (필수)"),
                                fieldWithPath("startAt").description("시작 일시 (yyyy-MM-dd'T'HH:mm 형식, 필수)"),
                                fieldWithPath("endAt").description("종료 일시 (yyyy-MM-dd'T'HH:mm 형식, 필수)"),
                                fieldWithPath("groupId").description("그룹 ID (필수)"),
                                fieldWithPath("description").description("여행 설명 (100글자)").optional(),
                                fieldWithPath("memberUuids").description("member UUID list (optional)").optional()
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(travelItineraryService, never()).saveTravels(any(TravelItinerarySaveRequestDto.class), any());
    }

    @Test
    @DisplayName("로그인 사용자의 CSRF 토큰이 유효하지 않으면 여행 일정 생성 요청은 403을 반환한다.")
    void 로그인_사용자의_CSRF_토큰이_유효하지_않으면_여행_일정_생성_요청은_403을_반환한다() throws Exception {
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(false);

        String requestBody = buildTravelSaveRequestBody(
                "제목", "2026-02-15T00:00", "2026-02-18T00:00", 1L, "설명"
        );

        mockMvc.perform(post("/travels")
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("여행 일정 생성 요청 본문이 유효하지 않으면 400을 반환한다.")
    void 여행_일정_생성_요청_본문이_유효하지_않으면_400을_반환한다() throws Exception {
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);

        String invalidBody = buildTravelSaveRequestBody(
                " ", "2026-02-15T00:00", "2026-02-18T00:00", 1L, "설명"
        );

        mockMvc.perform(post("/travels")
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("제목은 필수입니다."))
                .andDo(document("travels/create-fail-bad-request",
                        requestFields(
                                fieldWithPath("title").description("여행 제목 (필수)"),
                                fieldWithPath("startAt").description("시작 일시 (yyyy-MM-dd'T'HH:mm 형식, 필수)"),
                                fieldWithPath("endAt").description("종료 일시 (yyyy-MM-dd'T'HH:mm 형식, 필수)"),
                                fieldWithPath("groupId").description("그룹 ID (필수)"),
                                fieldWithPath("description").description("여행 설명 (100글자)").optional(),
                                fieldWithPath("memberUuids").description("member UUID list (optional)").optional()
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(travelItineraryService, never()).saveTravels(any(TravelItinerarySaveRequestDto.class), any());
    }

    @Test
    @DisplayName("그룹 멤버가 아닌 사용자의 여행 일정 생성 요청 시 403을 반환한다.")
    void 그룹_멤버가_아닌_사용자의_여행_일정_생성_요청_시_403을_반환한다() throws Exception {
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);
        given(travelItineraryService.saveTravels(any(TravelItinerarySaveRequestDto.class), eq(1L)))
                .willThrow(new BusinessException(TravelItineraryErrorCode.SAVE_FORBIDDEN));

        String requestBody = buildTravelSaveRequestBody(
                "제목", "2026-02-15T00:00", "2026-02-18T00:00", 1L, "설명"
        );

        mockMvc.perform(post("/travels")
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("그룹 내 그룹원만 여행을 생성할 수 있습니다."))
                .andDo(document("travels/create-fail-save-forbidden",
                        requestFields(
                                fieldWithPath("title").description("여행 제목 (필수)"),
                                fieldWithPath("startAt").description("시작 일시 (yyyy-MM-dd'T'HH:mm 형식, 필수)"),
                                fieldWithPath("endAt").description("종료 일시 (yyyy-MM-dd'T'HH:mm 형식, 필수)"),
                                fieldWithPath("groupId").description("그룹 ID (필수)"),
                                fieldWithPath("description").description("여행 설명 (100글자)").optional(),
                                fieldWithPath("memberUuids").description("member UUID list (optional)").optional()
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("시작일 LocalDateTime 포맷 아닐 시 DateTimeParseException(타임존 포함의 경우)")
    void 시작일_포맷_예외() throws Exception {
        //given
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);

        String requestBody = buildTravelSaveRequestBody(
                "제목", "2026-02-15Z00:00", "2026-02-18T00:00", 1L, "설명"
        );

        //when, then
        mockMvc.perform(
                post("/travels")
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        ).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("시작일 월 경계값 13월일 시 예외")
    void 시작일_월_경계값_예외() throws Exception {
        //given
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);

        String requestBody = buildTravelSaveRequestBody(
                "제목", "2026-13-15T00:00", "2027-02-18T00:00", 1L, "설명"
        );

        //when, then
        mockMvc.perform(
                post("/travels")
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        ).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("여행 업데이트 요청 성공 시 200 반환")
    void 여행_업데이트_요청_성공() throws Exception {
        // given
        Long travelId = 1L;
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);

        String requestBody = buildTravelUpdateRequestBody(
                "수정 제목",
                "2026-02-20T00:00",
                "2026-02-22T00:00",
                "수정 설명"
        );

        // when, then
        mockMvc.perform(patch("/travels/{travelId}", travelId)
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andDo(document("travels/update",
                        pathParameters(
                                parameterWithName("travelId").description("수정할 여행 일정 ID")
                        ),
                        requestFields(
                                fieldWithPath("title").description("여행 제목").optional(),
                                fieldWithPath("startAt").description("시작 일시 (yyyy-MM-dd'T'HH:mm)").optional(),
                                fieldWithPath("endAt").description("종료 일시 (yyyy-MM-dd'T'HH:mm)").optional(),
                                fieldWithPath("description").description("여행 설명 (최대 100자)").optional()
                        )
                ));
    }

    @Test
    @DisplayName("비로그인 사용자가 여행 업데이트 요청 시 401을 반환한다.")
    void 비로그인_사용자가_여행_업데이트_요청_시_401을_반환한다() throws Exception {
        given(sessionManager.getUserIdOrThrow(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

        String requestBody = buildTravelUpdateRequestBody(
                "수정 제목", "2026-02-20T00:00", "2026-02-22T00:00", "수정 설명"
        );

        mockMvc.perform(patch("/travels/{travelId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("travels/update-fail-unauthorized",
                        pathParameters(
                                parameterWithName("travelId").description("수정할 여행 일정 ID")
                        ),
                        requestFields(
                                fieldWithPath("title").description("여행 제목").optional(),
                                fieldWithPath("startAt").description("시작 일시 (yyyy-MM-dd'T'HH:mm)").optional(),
                                fieldWithPath("endAt").description("종료 일시 (yyyy-MM-dd'T'HH:mm)").optional(),
                                fieldWithPath("description").description("여행 설명 (최대 100자)").optional()
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("로그인 사용자의 CSRF 토큰이 유효하지 않으면 여행 업데이트 요청은 403을 반환한다.")
    void 로그인_사용자의_CSRF_토큰이_유효하지_않으면_여행_업데이트_요청은_403을_반환한다() throws Exception {
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(false);

        String requestBody = buildTravelUpdateRequestBody(
                "수정 제목", "2026-02-20T00:00", "2026-02-22T00:00", "수정 설명"
        );

        mockMvc.perform(patch("/travels/{travelId}", 1L)
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("여행 업데이트 요청 본문이 유효하지 않으면 400을 반환한다.")
    void 여행_업데이트_요청_본문이_유효하지_않으면_400을_반환한다() throws Exception {
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);

        String longDescription = "a".repeat(101);
        String requestBody = buildTravelUpdateRequestBody(
                "수정 제목", "2026-02-20T00:00", "2026-02-22T00:00", longDescription
        );

        mockMvc.perform(patch("/travels/{travelId}", 1L)
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("여행 설명은 최대 100자까지 입력할 수 있습니다."))
                .andDo(document("travels/update-fail-bad-request",
                        pathParameters(
                                parameterWithName("travelId").description("수정할 여행 일정 ID")
                        ),
                        requestFields(
                                fieldWithPath("title").description("여행 제목").optional(),
                                fieldWithPath("startAt").description("시작 일시 (yyyy-MM-dd'T'HH:mm)").optional(),
                                fieldWithPath("endAt").description("종료 일시 (yyyy-MM-dd'T'HH:mm)").optional(),
                                fieldWithPath("description").description("여행 설명 (최대 100자)").optional()
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(travelItineraryService, never()).updateTravel(any(), any(), any());
    }

    @Test
    @DisplayName("여행 리더가 아닌 사용자의 여행 업데이트 요청 시 401을 반환한다.")
    void 여행_리더가_아닌_사용자의_여행_업데이트_요청_시_401을_반환한다() throws Exception {
        Long travelId = 1L;
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);
        doThrow(new BusinessException(UserTravelItineraryErrorCode.UPDATE_UNAUTHORIZED))
                .when(travelItineraryService).updateTravel(any(), eq(travelId), eq(1L));

        String requestBody = buildTravelUpdateRequestBody(
                "수정 제목", "2026-02-20T00:00", "2026-02-22T00:00", "수정 설명"
        );

        mockMvc.perform(patch("/travels/{travelId}", travelId)
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("LEADER만 수정할 수 있습니다."))
                .andDo(document("travels/update-fail-update-unauthorized",
                        pathParameters(
                                parameterWithName("travelId").description("수정할 여행 일정 ID")
                        ),
                        requestFields(
                                fieldWithPath("title").description("여행 제목").optional(),
                                fieldWithPath("startAt").description("시작 일시 (yyyy-MM-dd'T'HH:mm)").optional(),
                                fieldWithPath("endAt").description("종료 일시 (yyyy-MM-dd'T'HH:mm)").optional(),
                                fieldWithPath("description").description("여행 설명 (최대 100자)").optional()
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("여행 일정 삭제 요청 성공 시 200을 반환한다.")
    void 여행_일정_삭제_요청_성공() throws Exception {
        Long travelId = 1L;
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);
        doNothing().when(travelItineraryService).deleteTravel(travelId, 1L);

        mockMvc.perform(delete("/travels/{travelId}", travelId)
                        .with(loginJwt()))
                        .andExpect(status().isOk())
                        .andDo(document("travels/delete",
                                pathParameters(
                                parameterWithName("travelId").description("삭제할 여행 일정 ID")
                        )
                ));
    }

    @Test
    @DisplayName("비로그인 사용자가 여행 일정 삭제 요청 시 401을 반환한다.")
    void 비로그인_사용자가_여행_일정_삭제_요청_시_401을_반환한다() throws Exception {
        given(sessionManager.getUserIdOrThrow(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

        mockMvc.perform(delete("/travels/{travelId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("travels/delete-fail-unauthorized",
                        pathParameters(
                                parameterWithName("travelId").description("삭제할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(travelItineraryService, never()).deleteTravel(any(), any());
    }

    @Test
    @DisplayName("여행 리더가 아닌 사용자의 여행 일정 삭제 요청 시 401을 반환한다.")
    void 여행_리더가_아닌_사용자의_여행_일정_삭제_요청_시_401을_반환한다() throws Exception {
        Long travelId = 1L;
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);
        doThrow(new BusinessException(UserTravelItineraryErrorCode.DELETE_UNAUTHORIZED))
                .when(travelItineraryService).deleteTravel(travelId, 1L);

        mockMvc.perform(delete("/travels/{travelId}", travelId)
                        .with(loginJwt()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("LEADER만 삭제할 수 있습니다."))
                .andDo(document("travels/delete-fail-delete-unauthorized",
                        pathParameters(
                                parameterWithName("travelId").description("삭제할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("여행 탈퇴 요청 성공 시 200을 반환한다.")
    void 여행_탈퇴_요청_성공() throws Exception {
        Long travelId = 1L;
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);
        doNothing().when(travelItineraryService).leaveTravel(travelId, 1L);

        mockMvc.perform(delete("/travels/{travelId}/users/me", travelId)
                        .with(loginJwt()))
                .andExpect(status().isOk())
                .andDo(document("travels/leave",
                        pathParameters(
                                parameterWithName("travelId").description("탈퇴할 여행 일정 ID")
                        )
                ));
    }

    @Test
    @DisplayName("비로그인 사용자가 여행 탈퇴 요청 시 401을 반환한다.")
    void 비로그인_사용자가_여행_탈퇴_요청_시_401을_반환한다() throws Exception {
        given(sessionManager.getUserIdOrThrow(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

        mockMvc.perform(delete("/travels/{travelId}/users/me", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("travels/leave-fail-unauthorized",
                        pathParameters(
                                parameterWithName("travelId").description("탈퇴할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("여행 리더의 여행 탈퇴 요청 시 401을 반환한다.")
    void 여행_리더의_여행_탈퇴_요청_시_401을_반환한다() throws Exception {
        Long travelId = 1L;
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);
        doThrow(new BusinessException(UserTravelItineraryErrorCode.LEAVE_UNAUTHORIZED))
                .when(travelItineraryService).leaveTravel(travelId, 1L);

        mockMvc.perform(delete("/travels/{travelId}/users/me", travelId)
                        .with(loginJwt()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("LEADER는 여행에서 탈퇴할 수 없습니다."))
                .andDo(document("travels/leave-fail-leave-unauthorized",
                        pathParameters(
                                parameterWithName("travelId").description("탈퇴할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("여행 참가 요청 성공 시 200을 반환한다.")
    void 여행_참가_요청_성공() throws Exception {
        Long travelId = 1L;
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);
        doNothing().when(travelItineraryService).joinTravel(travelId, 1L);

        mockMvc.perform(post("/travels/{travelId}/users/me", travelId)
                        .with(loginJwt()))
                .andExpect(status().isOk())
                .andDo(document("travels/join",
                        pathParameters(
                                parameterWithName("travelId").description("참가할 여행 일정 ID")
                        )
                ));

        verify(travelItineraryService, times(1)).joinTravel(travelId, 1L);
    }

    @Test
    @DisplayName("비로그인 사용자가 여행 참가 요청 시 401을 반환한다.")
    void 비로그인_사용자가_여행_참가_요청_시_401을_반환한다() throws Exception {
        given(sessionManager.getUserIdOrThrow(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

        mockMvc.perform(post("/travels/{travelId}/users/me", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("travels/join-fail-unauthorized",
                        pathParameters(
                                parameterWithName("travelId").description("참가할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(travelItineraryService, never()).joinTravel(any(), any());
    }

    @Test
    @DisplayName("이미 참가한 사용자의 여행 참가 요청 시 409를 반환한다.")
    void 이미_참가한_사용자의_여행_참가_요청_시_409를_반환한다() throws Exception {
        Long travelId = 1L;
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);
        doThrow(new BusinessException(UserTravelItineraryErrorCode.ALREADY_JOINED_TRAVEL))
                .when(travelItineraryService).joinTravel(travelId, 1L);

        mockMvc.perform(post("/travels/{travelId}/users/me", travelId)
                        .with(loginJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 참가한 여행입니다."))
                .andDo(document("travels/join-fail-already-joined-travel",
                        pathParameters(
                                parameterWithName("travelId").description("참가할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("그룹 여행 목록 조회 요청 성공 시 200을 반환한다.")
    void 그룹_여행_목록_조회_요청_성공_시_200을_반환한다() throws Exception {
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(travelItineraryService.browseTravels(any(), any(), anyInt(), any())).willReturn(
                new TravelItineraryCursorResponseDto(
                        List.of(
                                new TravelItineraryCursorResponseDto.TravelSummaryDto(
                                        1L,
                                        "제주도 뚜벅코 탐험",
                                        "제주 맛집 투어",
                                        LocalDateTime.of(2026, 3, 1, 0, 0),
                                        LocalDateTime.of(2026, 3, 5, 0, 0),
                                        3
                                )
                        ),
                        100L,
                        true,
                        15
                )
        );

        mockMvc.perform(get("/travels/{groupId}", 10L)
                        .with(loginJwt())
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(1L))
                .andExpect(jsonPath("$.items[0].title").value("제주도 뚜벅코 탐험"))
                .andExpect(jsonPath("$.items[0].description").value("제주 맛집 투어"))
                .andExpect(jsonPath("$.items[0].startAt").value("2026-03-01T00:00:00"))
                .andExpect(jsonPath("$.items[0].endAt").value("2026-03-05T00:00:00"))
                .andExpect(jsonPath("$.items[0].memberCount").value(3))
                .andExpect(jsonPath("$.nextCursor").value(100L))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.count").value(15))
                .andDo(document("travels/list",
                        pathParameters(
                                parameterWithName("groupId").description("그룹 ID")
                        ),
                        queryParameters(
                                parameterWithName("cursor").optional().description("다음 페이지 커서"),
                                parameterWithName("size").optional().description("조회할 개수")
                        ),
                        responseFields(
                                fieldWithPath("items").description("여행 일정 목록"),
                                fieldWithPath("items[].id").description("여행 일정 ID"),
                                fieldWithPath("items[].title").description("여행 제목"),
                                fieldWithPath("items[].description").description("여행 설명").optional(),
                                fieldWithPath("items[].startAt").description("시작 일시"),
                                fieldWithPath("items[].endAt").description("종료 일시"),
                                fieldWithPath("items[].memberCount").description("현재 인원"),
                                fieldWithPath("nextCursor").description("다음 페이지 커서").optional(),
                                fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                                fieldWithPath("count").description("그룹 내 생성 여행 개수")
                        )
                ));
    }

    @Test
    @DisplayName("비로그인 사용자의 여행 목록 조회 요청 시 count 응답을 반환한다.")
    void 비로그인_사용자의_여행_목록_조회_요청_시_count_응답을_반환한다() throws Exception {
        given(sessionManager.getUserId(any())).willReturn(null);
        given(travelItineraryService.browseTravels(eq(10L), eq(null), eq(10), eq(null)))
                .willReturn(TravelItineraryCursorResponseDto.countOnly(3L));

        mockMvc.perform(get("/travels/{groupId}", 10L)
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.count").value(3))
                .andDo(document("travels/list-anonymous",
                        pathParameters(
                                parameterWithName("groupId").description("그룹 ID")
                        ),
                        queryParameters(
                                parameterWithName("cursor").optional().description("다음 페이지 커서"),
                                parameterWithName("size").optional().description("조회할 개수")
                        ),
                        responseFields(
                                fieldWithPath("items").description("여행 일정 목록 (비로그인 시 빈 배열)"),
                                fieldWithPath("nextCursor").description("다음 페이지 커서").optional(),
                                fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                                fieldWithPath("count").description("그룹 내 생성 여행 개수")
                        )
                ));

        verify(travelItineraryService, times(1)).browseTravels(eq(10L), eq(null), eq(10), eq(null));
    }

    @Test
    @DisplayName("그룹 멤버가 아닌 사용자의 여행 목록 조회 요청 시 count 응답을 반환한다.")
    void 그룹_멤버가_아닌_사용자의_여행_목록_조회_요청_시_count_응답을_반환한다() throws Exception {
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(travelItineraryService.browseTravels(eq(10L), eq(null), eq(10), eq(1L)))
                .willReturn(TravelItineraryCursorResponseDto.countOnly(3L));

        mockMvc.perform(get("/travels/{groupId}", 10L)
                        .with(loginJwt())
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.count").value(3))
                .andDo(document("travels/list-non-member",
                        pathParameters(
                                parameterWithName("groupId").description("그룹 ID")
                        ),
                        queryParameters(
                                parameterWithName("cursor").optional().description("다음 페이지 커서"),
                                parameterWithName("size").optional().description("조회할 개수")
                        ),
                        responseFields(
                                fieldWithPath("items").description("여행 일정 목록 (그룹 비멤버 시 빈 배열)"),
                                fieldWithPath("nextCursor").description("다음 페이지 커서").optional(),
                                fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                                fieldWithPath("count").description("그룹 내 생성 여행 개수")
                        )
                ));

        verify(travelItineraryService, times(1)).browseTravels(eq(10L), eq(null), eq(10), eq(1L));
    }

    @Test
    @DisplayName("여행 메타 정보 조회 성공 시 200을 반환한다.")
    void 여행_메타_정보_조회_성공() throws Exception {
        Long travelId = 1L;
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(travelItineraryService.getTravelInfo(travelId, 1L)).willReturn(
                new TravelItineraryInfoResponseDto(
                        "제주도 뚜벅코 탐험",
                        LocalDateTime.of(2026, 3, 1, 0, 0),
                        LocalDateTime.of(2026, 3, 5, 0, 0),
                        List.of(
                                new TravelItineraryInfoResponseDto.TravelMemberDto("철수", "http://img1", UserRole.LEADER),
                                new TravelItineraryInfoResponseDto.TravelMemberDto("영희", "http://img2", UserRole.MEMBER)
                        )
                )
        );

        mockMvc.perform(get("/travels/{travelId}/info", travelId)
                        .with(loginJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제주도 뚜벅코 탐험"))
                .andExpect(jsonPath("$.startAt").value("2026-03-01T00:00:00"))
                .andExpect(jsonPath("$.endAt").value("2026-03-05T00:00:00"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].nickname").value("철수"))
                .andExpect(jsonPath("$.members[0].userRole").value("LEADER"))
                .andExpect(jsonPath("$.members[1].nickname").value("영희"))
                .andExpect(jsonPath("$.members[1].userRole").value("MEMBER"))
                .andDo(document("travels/info",
                        pathParameters(
                                parameterWithName("travelId").description("조회할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("title").description("여행 제목"),
                                fieldWithPath("startAt").description("시작 일시"),
                                fieldWithPath("endAt").description("종료 일시"),
                                fieldWithPath("members").description("여행 멤버 목록"),
                                fieldWithPath("members[].nickname").description("멤버 닉네임"),
                                fieldWithPath("members[].profileUrl").description("멤버 프로필 이미지 URL").optional(),
                                fieldWithPath("members[].userRole").description("멤버 역할 (LEADER / MEMBER)")
                        )
                ));

        verify(travelItineraryService, times(1)).getTravelInfo(travelId, 1L);
    }

    @Test
    @DisplayName("비로그인 사용자가 여행 메타 정보 조회 시 401을 반환한다.")
    void 비로그인_사용자가_여행_메타_정보_조회_시_401을_반환한다() throws Exception {
        given(sessionManager.getUserIdOrThrow(any()))
                .willThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/travels/{travelId}/info", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("travels/info-fail-unauthorized",
                        pathParameters(
                                parameterWithName("travelId").description("조회할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(travelItineraryService, never()).getTravelInfo(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 여행 메타 정보 조회 시 404를 반환한다.")
    void 존재하지_않는_여행_메타_정보_조회_시_404를_반환한다() throws Exception {
        Long travelId = 999L;
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(travelItineraryService.getTravelInfo(travelId, 1L))
                .willThrow(new BusinessException(TravelItineraryErrorCode.TRAVEL_NOT_FOUND));

        mockMvc.perform(get("/travels/{travelId}/info", travelId)
                        .with(loginJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("해당 여행이 존재하지 않습니다."))
                .andDo(document("travels/info-fail-not-found",
                        pathParameters(
                                parameterWithName("travelId").description("조회할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("여행 멤버가 아닌 사용자가 여행 메타 정보 조회 시 404를 반환한다.")
    void 여행_멤버가_아닌_사용자가_여행_메타_정보_조회_시_404를_반환한다() throws Exception {
        Long travelId = 1L;
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(travelItineraryService.getTravelInfo(travelId, 1L))
                .willThrow(new BusinessException(UserTravelItineraryErrorCode.USER_TRAVEL_ITINERARY_NOT_FOUND));

        mockMvc.perform(get("/travels/{travelId}/info", travelId)
                        .with(loginJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("여행 내 해당 유저를 찾을 수 없습니다."))
                .andDo(document("travels/info-fail-member-not-found",
                        pathParameters(
                                parameterWithName("travelId").description("조회할 여행 일정 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    private String buildTravelSaveRequestBody(
            String title,
            String startAt,
            String endAt,
            Long groupId,
            String description
    ) {
        return buildTravelSaveRequestBody(title, startAt, endAt, groupId, description, "[]");
    }

    private String buildTravelSaveRequestBody(
            String title,
            String startAt,
            String endAt,
            Long groupId,
            String description,
            String memberUuidsJson
    ) {
        String answer =
                """
                {
                  "title": "%s",
                  "startAt": "%s",
                  "endAt": "%s",
                  "groupId": %d,
                  "description": "%s",
                  "memberUuids": %s
                }
                """;
        return answer.formatted(title, startAt, endAt, groupId, description, memberUuidsJson);
    }

    private String buildTravelUpdateRequestBody(
            String title,
            String startAt,
            String endAt,
            String description
    ) {
        return """
                {
                  "title": "%s",
                  "startAt": "%s",
                  "endAt": "%s",
                  "description": "%s"
                }
                """.formatted(title, startAt, endAt, description);
    }

    private RequestPostProcessor loginJwt() {
        return request -> {
            request.addHeader("Authorization", "Bearer test-token");
            return request;
        };
    }

    interface SessionManager {
        Long getUserId(Object request);

        Long getUserIdOrThrow(Object request);
    }

    interface CsrfTokenManager {
        boolean isValid(Object request, Object response);
    }
}
