package org.triple.backend.travel.unit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.travel.controller.TravelItineraryController;
import org.triple.backend.travel.dto.request.TravelItinerarySaveRequestDto;
import org.triple.backend.travel.dto.response.TravelItineraryCursorResponseDto;
import org.triple.backend.travel.dto.response.TravelItinerarySaveResponseDto;
import org.triple.backend.travel.service.TravelItineraryService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
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
                "제목", "2026-02-15T00:00", "2026-02-18T00:00", 1L, "설명", "test-url", 5
        );

        //when, then
        mockMvc.perform(post("/travels")
                        .requestAttr("LOGIN_USER_ID", 1L)
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
                                        fieldWithPath("thumbnailUrl").description("썸네일 URL").optional(),
                                        fieldWithPath("memberLimit").description("최대 인원 (최소 1, 필수))")
                                ),
                                responseFields(
                                        fieldWithPath("itineraryId").description("여행 일정 ID")
                                )
                        )
                );
    }

    @Test
    @DisplayName("시작일 LocalDateTime 포맷 아닐 시 DateTimeParseException(타임존 포함의 경우)")
    void 시작일_포맷_예외() throws Exception {
        //given
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(csrfTokenManager.isValid(any(), any())).willReturn(true);

        String requestBody = buildTravelSaveRequestBody(
                "제목", "2026-02-15Z00:00", "2026-02-18T00:00", 1L, "설명", "test-url", 5
        );

        //when, then
        mockMvc.perform(
                post("/travels")
                        .requestAttr("LOGIN_USER_ID", 1L)
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
                "제목", "2026-13-15T00:00", "2027-02-18T00:00", 1L, "설명", "test-url", 5
        );

        //when, then
        mockMvc.perform(
                post("/travels")
                        .requestAttr("LOGIN_USER_ID", 1L)
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
                "수정 설명",
                "https://example.com/updated.png",
                10
        );

        // when, then
        mockMvc.perform(patch("/travels/{travelId}", travelId)
                        .requestAttr("LOGIN_USER_ID", 1L)
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
                                fieldWithPath("description").description("여행 설명 (최대 100자)").optional(),
                                fieldWithPath("thumbnailUrl").description("썸네일 URL").optional(),
                                fieldWithPath("memberLimit").description("멤버 수 제한 (1~20)").optional()
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
                        .requestAttr("LOGIN_USER_ID", 1L))
                        .andExpect(status().isOk())
                        .andDo(document("travels/delete",
                                pathParameters(
                                parameterWithName("travelId").description("삭제할 여행 일정 ID")
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
                        .requestAttr("LOGIN_USER_ID", 1L))
                .andExpect(status().isOk())
                .andDo(document("travels/leave",
                        pathParameters(
                                parameterWithName("travelId").description("탈퇴할 여행 일정 ID")
                        )
                ));
    }

    @Test
    @DisplayName("그룹 여행 목록 조회 요청 성공 시 200을 반환한다.")
    void browse_group_travels_success() throws Exception {
        given(sessionManager.getUserId(any())).willReturn(1L);
        given(sessionManager.getUserIdOrThrow(any())).willReturn(1L);
        given(travelItineraryService.browseTravels(any(), any(), anyInt(), any())).willReturn(
                new TravelItineraryCursorResponseDto(
                        List.of(
                                new TravelItineraryCursorResponseDto.TravelSummaryDto(
                                        "제주도 뚜벅코 탐험",
                                        "제주 맛집 투어",
                                        LocalDateTime.of(2026, 3, 1, 0, 0),
                                        LocalDateTime.of(2026, 3, 5, 0, 0),
                                        "https://example.com/thumb.png",
                                        3,
                                        5
                                )
                        ),
                        100L,
                        true
                )
        );

        mockMvc.perform(get("/travels/{groupId}", 10L)
                        .requestAttr("LOGIN_USER_ID", 1L)
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("제주도 뚜벅코 탐험"))
                .andExpect(jsonPath("$.items[0].description").value("제주 맛집 투어"))
                .andExpect(jsonPath("$.items[0].startAt").value("2026-03-01T00:00:00"))
                .andExpect(jsonPath("$.items[0].endAt").value("2026-03-05T00:00:00"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://example.com/thumb.png"))
                .andExpect(jsonPath("$.items[0].memberCount").value(3))
                .andExpect(jsonPath("$.items[0].memberLimit").value(5))
                .andExpect(jsonPath("$.nextCursor").value(100L))
                .andExpect(jsonPath("$.hasNext").value(true))
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
                                fieldWithPath("items[].title").description("여행 제목"),
                                fieldWithPath("items[].description").description("여행 설명").optional(),
                                fieldWithPath("items[].startAt").description("시작 일시"),
                                fieldWithPath("items[].endAt").description("종료 일시"),
                                fieldWithPath("items[].thumbnailUrl").description("썸네일 URL").optional(),
                                fieldWithPath("items[].memberCount").description("현재 인원"),
                                fieldWithPath("items[].memberLimit").description("최대 인원"),
                                fieldWithPath("nextCursor").description("다음 페이지 커서").optional(),
                                fieldWithPath("hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }

    private String buildTravelSaveRequestBody(Object... values) {
        String answer =
                """
                {
                  "title": "%s",
                  "startAt": "%s",
                  "endAt": "%s",
                  "groupId": %d,
                  "description": "%s",
                  "thumbnailUrl": "%s",
                  "memberLimit": %d
                }
                """;
        return answer.formatted(values);
    }

    private String buildTravelUpdateRequestBody(
            String title,
            String startAt,
            String endAt,
            String description,
            String thumbnailUrl,
            int memberLimit
    ) {
        return """
                {
                  "title": "%s",
                  "startAt": "%s",
                  "endAt": "%s",
                  "description": "%s",
                  "thumbnailUrl": "%s",
                  "memberLimit": %d
                }
                """.formatted(title, startAt, endAt, description, thumbnailUrl, memberLimit);
    }
}
