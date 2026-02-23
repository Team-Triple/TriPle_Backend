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
import org.triple.backend.group.dto.request.GroupUpdateRequestDto;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.dto.response.GroupDetailResponseDto;
import org.triple.backend.group.dto.response.GroupUpdateResponseDto;
import org.triple.backend.group.entity.group.GroupKind;
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
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN_KEY;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@WebMvcTest(GroupController.class)
public class GroupControllerTest extends ControllerTest {

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
    @DisplayName("로그인한 사용자는 그룹 상세 정보를 조회할 수 있다.")
    void 로그인한_사용자는_그룹_상세_정보를_조회할_수_있다() throws Exception {
        // given
        Long groupId = 1L;
        GroupDetailResponseDto response = new GroupDetailResponseDto(
                List.of(
                        new GroupDetailResponseDto.UserDto("상윤", "모임장", "http://img", true),
                        new GroupDetailResponseDto.UserDto("민규", "멤버", "http://img2", false)
                ),
                "여행모임",
                "3월 일본 여행",
                GroupKind.PRIVATE,
                "https://example.com/thumb.png",
                2,
                10
        );

        given(groupService.detail(eq(groupId), eq(1L)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/groups/{groupId}", groupId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].name").value("상윤"))
                .andExpect(jsonPath("$.users[0].isOwner").value(true))
                .andExpect(jsonPath("$.name").value("여행모임"))
                .andExpect(jsonPath("$.description").value("3월 일본 여행"))
                .andExpect(jsonPath("$.groupKind").value("PRIVATE"))
                .andExpect(jsonPath("$.thumbNailUrl").value("https://example.com/thumb.png"))
                .andExpect(jsonPath("$.currentMemberCount").value(2))
                .andExpect(jsonPath("$.memberLimit").value(10))
                .andDo(document("groups/detail",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("조회할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("users").description("그룹 멤버 목록"),
                                fieldWithPath("users[].name").description("멤버 이름"),
                                fieldWithPath("users[].description").description("멤버 소개").optional(),
                                fieldWithPath("users[].profileUrl").description("멤버 프로필 이미지 URL").optional(),
                                fieldWithPath("users[].isOwner").description("방장 여부"),
                                fieldWithPath("name").description("그룹 이름"),
                                fieldWithPath("description").description("그룹 설명"),
                                fieldWithPath("groupKind").description("그룹 종류"),
                                fieldWithPath("thumbNailUrl").description("그룹 썸네일 URL").optional(),
                                fieldWithPath("currentMemberCount").description("현재 인원"),
                                fieldWithPath("memberLimit").description("최대 인원")
                        )
                ));

        verify(groupService, times(1)).detail(groupId, 1L);
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

    @Test
    @DisplayName("그룹을 수정하면 수정된 그룹 정보를 반환한다.")
    void 그룹을_수정하면_수정된_그룹_정보를_반환한다() throws Exception {
        // given
        Long groupId = 1L;
        GroupUpdateResponseDto response = new GroupUpdateResponseDto(
                groupId,
                GroupKind.PRIVATE,
                "수정모임",
                "수정설명",
                "https://example.com/updated.png",
                20,
                1
        );

        given(groupService.update(any(GroupUpdateRequestDto.class), eq(groupId), eq(1L)))
                .willReturn(response);
        mockCsrfValid();

        String body = """
                {
                  "groupKind": "PRIVATE",
                  "name": "수정모임",
                  "description": "수정설명",
                  "thumbNailUrl": "https://example.com/updated.png",
                  "memberLimit": 20
                }
                """;

        // when & then
        mockMvc.perform(patch("/groups/{groupId}", groupId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(groupId))
                .andExpect(jsonPath("$.groupKind").value("PRIVATE"))
                .andExpect(jsonPath("$.name").value("수정모임"))
                .andExpect(jsonPath("$.description").value("수정설명"))
                .andExpect(jsonPath("$.thumbNailUrl").value("https://example.com/updated.png"))
                .andExpect(jsonPath("$.memberLimit").value(20))
                .andExpect(jsonPath("$.currentMemberCount").value(1))
                .andDo(document("groups/update",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("수정할 그룹 ID")
                        ),
                        requestFields(
                                fieldWithPath("groupKind").description("그룹 종류 (PUBLIC, PRIVATE)"),
                                fieldWithPath("name").description("그룹 이름"),
                                fieldWithPath("description").description("그룹 설명"),
                                fieldWithPath("thumbNailUrl").description("썸네일 이미지 URL"),
                                fieldWithPath("memberLimit").description("최대 인원(1~50)")
                        ),
                        responseFields(
                                fieldWithPath("groupId").description("그룹 ID"),
                                fieldWithPath("groupKind").description("그룹 종류"),
                                fieldWithPath("name").description("그룹 이름"),
                                fieldWithPath("description").description("그룹 설명"),
                                fieldWithPath("thumbNailUrl").description("썸네일 이미지 URL"),
                                fieldWithPath("memberLimit").description("최대 인원"),
                                fieldWithPath("currentMemberCount").description("현재 인원")
                        )
                ));

        verify(groupService, times(1)).update(any(GroupUpdateRequestDto.class), eq(groupId), eq(1L));
        verify(csrfTokenManager, times(1)).isValid(any(HttpServletRequest.class), any(String.class));
    }

    @Test
    @DisplayName("그룹 수정 요청 본문이 유효하지 않으면 400을 반환한다.")
    void 그룹_수정_요청_본문이_유효하지_않으면_400을_반환한다() throws Exception {
        // given
        Long groupId = 1L;
        mockCsrfValid();

        String invalidBody = """
                {
                  "groupKind": "PRIVATE",
                  "name": " ",
                  "description": "수정설명",
                  "thumbNailUrl": "https://example.com/updated.png",
                  "memberLimit": 20
                }
                """;

        // when & then
        mockMvc.perform(patch("/groups/{groupId}", groupId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());

        verify(groupService, never()).update(any(GroupUpdateRequestDto.class), any(Long.class), any(Long.class));
    }

    private void mockCsrfValid() {
        when(csrfTokenManager.isValid(any(HttpServletRequest.class), any(String.class))).thenReturn(true);
    }

    private RequestPostProcessor loginSessionAndCsrf() {
        return request -> {
            request.getSession(true).setAttribute(USER_SESSION_KEY, 1L);
            request.getSession().setAttribute(CSRF_TOKEN_KEY, CSRF_TOKEN);
            request.addHeader(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN);
            return request;
        };
    }
}
