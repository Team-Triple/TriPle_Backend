package org.triple.backend.group.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.group.controller.GroupController;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.service.GroupService;
import org.triple.backend.auth.session.CsrfTokenManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
public class GroupControllerTest extends ControllerTest {

    private static final String USER_SESSION_KEY = "USER_ID";
    private static final String CSRF_TOKEN = "csrf-token";

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @Test
    @DisplayName("그룹을 생성하면 그룹 정보를 반환한다.")
    void 그룹을_생성하면_그룹_정보를_반환한다() throws Exception {
        //given
        CreateGroupResponseDto response = new CreateGroupResponseDto(1L);

        given(groupService.create(any(CreateGroupRequestDto.class), eq(1L)))
                .willReturn(response);

        mockCsrfValid();

        String body = """
                {
                  "name": "여행모임",
                  "description": "3월 일본 여행",
                  "memberLimit": 10,
                  "groupKind": "PUBLIC",
                  "thumbNailUrl": "https://example.com/thumb.png"
                }
                """;

        //when & then
        mockMvc.perform(post("/groups")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(1L))
                .andDo(document("groups/create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("name").description("그룹 이름"),
                                fieldWithPath("description").description("그룹 설명"),
                                fieldWithPath("memberLimit").description("최대 인원(최소 1)").attributes(),
                                fieldWithPath("groupKind").description("그룹 종류 (PUBLIC, PRIVATE)"),
                                fieldWithPath("thumbNailUrl").description("썸네일 이미지 URL")
                        ),
                        responseFields(
                                fieldWithPath("groupId").description("그룹 ID")
                        )
                ));
    }

    @Test
    @DisplayName("공개 그룹 목록 첫 페이지를 조회한다.")
    void 공개_그룹_목록_첫_페이지를_조회한다() throws Exception {
        // given
        GroupCursorResponseDto response = new GroupCursorResponseDto(
                List.of(
                        new GroupCursorResponseDto.GroupSummaryDto(
                                10L,
                                "여행모임",
                                "3월 일본 여행",
                                1,
                                10,
                                "https://example.com/thumb.png"
                        )
                ),
                1L,
                true
        );

        given(groupService.browsePublicGroups(eq(null), eq(10)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/groups")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].groupId").value(10L))
                .andExpect(jsonPath("$.items[0].description").value("3월 일본 여행"))
                .andExpect(jsonPath("$.items[0].name").value("여행모임"))
                .andExpect(jsonPath("$.items[0].currentMemberCount").value(1))
                .andExpect(jsonPath("$.items[0].memberLimit").value(10))
                .andExpect(jsonPath("$.items[0].thumbNailUrl").value("https://example.com/thumb.png"))
                .andExpect(jsonPath("$.nextCursor").value(1L))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andDo(document("groups/browse-public",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("cursor").optional().description("커서(다음 페이지 조회 시 사용). 첫 페이지는 생략"),
                                parameterWithName("size").optional().description("페이지 크기(기본 10)")
                        ),
                        responseFields(
                                fieldWithPath("items").description("그룹 목록"),
                                fieldWithPath("items[].groupId").description("그룹 ID"),
                                fieldWithPath("items[].name").description("그룹 이름"),
                                fieldWithPath("items[].description").description("그룹 설명").optional(),
                                fieldWithPath("items[].thumbNailUrl").description("썸네일 URL").optional(),
                                fieldWithPath("items[].currentMemberCount").description("현재 인원").optional(),
                                fieldWithPath("items[].memberLimit").description("최대 인원").optional(),
                                fieldWithPath("nextCursor").description("다음 페이지 커서 (없으면 null)").optional(),
                                fieldWithPath("hasNext").description("다음 페이지 존재 여부")
                        )
                ));
    }


    @Test
    @DisplayName("그룹을 삭제합니다.")
    void 그룹을_삭제합니다() throws Exception {
        // given
        Long groupId = 1L;
        Long userId = 1L;

        doNothing().when(groupService).delete(groupId, userId);

        mockCsrfValid();

        // when & then
        mockMvc.perform(delete("/groups/{groupId}", groupId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("groups/delete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("삭제할 그룹 ID")
                        )
                ));

        verify(groupService, times(1)).delete(groupId, userId);
        verify(csrfTokenManager, times(1)).isValid(any(HttpServletRequest.class), any(String.class));
    }

    private void mockCsrfValid() {
        when(csrfTokenManager.isValid(any(HttpServletRequest.class), any(String.class))).thenReturn(true);
    }

    private RequestPostProcessor loginSessionAndCsrf() {
        return request -> {
            request.getSession(true).setAttribute(USER_SESSION_KEY, 1L);
            request.getSession().setAttribute(CsrfTokenManager.CSRF_TOKEN_KEY, CSRF_TOKEN);
            request.addHeader(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN);
            return request;
        };
    }
}