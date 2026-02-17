package org.triple.backend.travel.unit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.travel.controller.TravelController;
import org.triple.backend.travel.dto.request.TravelSaveRequestDto;
import org.triple.backend.travel.dto.response.TravelSaveResponseDto;
import org.triple.backend.travel.service.TravelService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TravelController.class)
class TravelControllerTest extends ControllerTest {

    @MockitoBean
    private TravelService travelService;

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

        TravelSaveResponseDto response = new TravelSaveResponseDto(1L);
        given(travelService.saveTravels(any(TravelSaveRequestDto.class), any()))
                .willReturn(response);

        String requestBody = buildTravelSaveRequestBody(
                "제목", "2026-02-15T00:00", "2026-02-18T00:00", 1L, "설명", "test-url", 5
        );

        //when, then
        mockMvc.perform(post("/travels")
                        .requestAttr("LOGIN_USER_ID", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                )
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
}
