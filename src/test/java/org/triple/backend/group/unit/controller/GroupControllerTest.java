package org.triple.backend.group.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.session.PublicUuidCodec;
import org.triple.backend.auth.session.UserIdentityResolver;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.controller.GroupController;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.request.GroupUpdateRequestDto;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.dto.response.GroupDetailResponseDto;
import org.triple.backend.group.dto.response.GroupMenuResponseDto;
import org.triple.backend.group.dto.response.GroupUpdateResponseDto;
import org.triple.backend.group.dto.response.GroupUsersResponseDto;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.service.GroupService;
import org.triple.backend.auth.session.CsrfTokenManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    @MockitoBean
    private UserIdentityResolver userIdentityResolver;

    @MockitoBean
    private PublicUuidCodec publicUuidCodec;

    @BeforeEach
    void setUp() {
        when(userIdentityResolver.resolve(any())).thenReturn(1L);
        when(publicUuidCodec.decryptOrThrow(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(publicUuidCodec.encrypt(any())).thenAnswer(invocation -> "enc-" + invocation.getArgument(0, String.class));
    }

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
    @DisplayName("비로그인 사용자가 그룹 생성 요청 시 401을 반환한다.")
    void 비로그인_사용자가_그룹_생성_요청_시_401을_반환한다() throws Exception {
        String body = """
                {
                  "name": "여행모임",
                  "description": "3월 일본 여행",
                  "memberLimit": 10,
                  "groupKind": "PUBLIC",
                  "thumbNailUrl": "https://example.com/thumb.png"
                }
                """;

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("groups/create-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("name").description("그룹 이름"),
                                fieldWithPath("description").description("그룹 설명"),
                                fieldWithPath("memberLimit").description("최대 인원(최소 1)"),
                                fieldWithPath("groupKind").description("그룹 종류 (PUBLIC, PRIVATE)"),
                                fieldWithPath("thumbNailUrl").description("썸네일 이미지 URL")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, never()).create(any(CreateGroupRequestDto.class), any(Long.class));
    }

    @Test
    @DisplayName("로그인 사용자의 CSRF 토큰이 유효하지 않으면 그룹 생성 요청은 403을 반환한다.")
    void 로그인_사용자의_CSRF_토큰이_유효하지_않으면_그룹_생성_요청은_403을_반환한다() throws Exception {
        given(csrfTokenManager.isValid(any(HttpServletRequest.class), any(String.class))).willReturn(false);

        String body = """
                {
                  "name": "여행모임",
                  "description": "3월 일본 여행",
                  "memberLimit": 10,
                  "groupKind": "PUBLIC",
                  "thumbNailUrl": "https://example.com/thumb.png"
                }
                """;

        mockMvc.perform(post("/groups")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF 토큰이 유효하지 않습니다."))
                .andDo(document("groups/create-fail-invalid-csrf-token",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("name").description("그룹 이름"),
                                fieldWithPath("description").description("그룹 설명"),
                                fieldWithPath("memberLimit").description("최대 인원(최소 1)"),
                                fieldWithPath("groupKind").description("그룹 종류 (PUBLIC, PRIVATE)"),
                                fieldWithPath("thumbNailUrl").description("썸네일 이미지 URL")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, never()).create(any(CreateGroupRequestDto.class), any(Long.class));
    }

    @Test
    @DisplayName("그룹 생성 요청 본문이 유효하지 않으면 400을 반환한다.")
    void 그룹_생성_요청_본문이_유효하지_않으면_400을_반환한다() throws Exception {
        mockCsrfValid();

        String invalidBody = """
                {
                  "name": " ",
                  "description": "3월 일본 여행",
                  "memberLimit": 10,
                  "groupKind": "PUBLIC",
                  "thumbNailUrl": "https://example.com/thumb.png"
                }
                """;

        mockMvc.perform(post("/groups")
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("그룹 이름은 필수입니다."))
                .andDo(document("groups/create-fail-bad-request",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("name").description("그룹 이름"),
                                fieldWithPath("description").description("그룹 설명"),
                                fieldWithPath("memberLimit").description("최대 인원(최소 1)"),
                                fieldWithPath("groupKind").description("그룹 종류 (PUBLIC, PRIVATE)"),
                                fieldWithPath("thumbNailUrl").description("썸네일 이미지 URL")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, never()).create(any(CreateGroupRequestDto.class), any(Long.class));
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

        given(groupService.search(eq(null), eq(null), eq(10)))
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
                                parameterWithName("keyword").optional().description("검색 키워드(없으면 전체 공개 그룹 조회)"),
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

        verify(groupService, times(1)).search(null, null, 10);
    }

    @Test
    @DisplayName("키워드가 있으면 공개 그룹 키워드 검색을 수행한다.")
    void 키워드가_있으면_공개_그룹_키워드_검색을_수행한다() throws Exception {
        // given
        GroupCursorResponseDto response = new GroupCursorResponseDto(
                List.of(
                        new GroupCursorResponseDto.GroupSummaryDto(
                                20L,
                                "제주여행",
                                "맛집 탐방",
                                1,
                                10,
                                "https://example.com/thumb.png"
                        )
                ),
                null,
                false
        );

        given(groupService.search(eq("제주"), eq(null), eq(10)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/groups")
                        .param("keyword", "제주")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("제주여행"));

        verify(groupService, times(1)).search("제주", null, 10);
    }

    @Test
    @DisplayName("키워드 길이가 20자를 초과하면 400을 반환한다.")
    void 키워드_길이가_20자를_초과하면_400을_반환한다() throws Exception {
        // given
        String keyword = "aaaaaaaaaaaaaaaaaaaaa";
        given(groupService.search(eq(keyword), eq(null), eq(10)))
                .willThrow(new BusinessException(GroupErrorCode.INVALID_SEARCH_KEYWORD_LENGTH));

        // when & then
        mockMvc.perform(get("/groups")
                        .param("keyword", keyword)
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("검색어는 최대 20자까지 입력할 수 있습니다."))
                .andDo(document("groups/browse-public-fail-invalid-keyword",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("keyword").optional().description("검색 키워드(없으면 전체 공개 그룹 조회)"),
                                parameterWithName("cursor").optional().description("커서(다음 페이지 조회 시 사용). 첫 페이지는 생략"),
                                parameterWithName("size").optional().description("페이지 크기(기본 10)")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, times(1)).search(keyword, null, 10);
    }

    @Test
    @DisplayName("로그인 사용자는 내가 속한 그룹 목록을 조회할 수 있다.")
    void 로그인_사용자는_내가_속한_그룹_목록을_조회할_수_있다() throws Exception {
        // given
        GroupCursorResponseDto response = new GroupCursorResponseDto(
                List.of(
                        new GroupCursorResponseDto.GroupSummaryDto(
                                30L,
                                "제주모임",
                                "제주도 여행",
                                3,
                                10,
                                "https://example.com/jeju.png"
                        )
                ),
                30L,
                true
        );

        given(groupService.myGroups(eq(null), eq(10), eq(1L)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/groups/me")
                        .with(loginSessionAndCsrf())
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].groupId").value(30L))
                .andExpect(jsonPath("$.items[0].name").value("제주모임"))
                .andExpect(jsonPath("$.items[0].description").value("제주도 여행"))
                .andExpect(jsonPath("$.items[0].currentMemberCount").value(3))
                .andExpect(jsonPath("$.items[0].memberLimit").value(10))
                .andExpect(jsonPath("$.items[0].thumbNailUrl").value("https://example.com/jeju.png"))
                .andExpect(jsonPath("$.nextCursor").value(30L))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andDo(document("groups/my-groups",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("cursor").optional().description("커서(다음 페이지 조회 시 사용). 첫 페이지는 생략"),
                                parameterWithName("size").optional().description("페이지 크기(기본 10)")
                        ),
                        responseFields(
                                fieldWithPath("items").description("내가 속한 그룹 목록"),
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

        verify(groupService, times(1)).myGroups(eq(null), eq(10), eq(1L));
    }

    @Test
    @DisplayName("비로그인 사용자가 내 그룹 목록을 조회하면 401을 반환한다.")
    void 비로그인_사용자가_내_그룹_목록을_조회하면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/groups/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("groups/my-groups-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("cursor").optional().description("커서(다음 페이지 조회 시 사용). 첫 페이지는 생략"),
                                parameterWithName("size").optional().description("페이지 크기(기본 10)")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, never()).myGroups(any(), anyInt(), any());
    }

    @Test
    @DisplayName("로그인 사용자는 그룹 메뉴 정보를 조회할 수 있다.")
    void 로그인_사용자는_그룹_메뉴_정보를_조회할_수_있다() throws Exception {
        // given
        Long groupId = 10L;
        GroupMenuResponseDto response = new GroupMenuResponseDto(
                "즐거운 여행단",
                "MBTI P들의 모임입니다. 맛집 탐방!",
                6,
                10,
                "https://example.com/thumb.png",
                Role.MEMBER
        );
        given(groupService.menu(eq(1L), eq(groupId))).willReturn(response);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/menu", groupId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("즐거운 여행단"))
                .andExpect(jsonPath("$.description").value("MBTI P들의 모임입니다. 맛집 탐방!"))
                .andExpect(jsonPath("$.currentMemberCount").value(6))
                .andExpect(jsonPath("$.memberLimit").value(10))
                .andExpect(jsonPath("$.thumbNailUrl").value("https://example.com/thumb.png"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andDo(document("groups/menu",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("메뉴 정보를 조회할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("name").description("그룹 이름"),
                                fieldWithPath("description").description("그룹 설명"),
                                fieldWithPath("currentMemberCount").description("현재 인원"),
                                fieldWithPath("memberLimit").description("최대 인원"),
                                fieldWithPath("thumbNailUrl").description("그룹 썸네일 URL").optional(),
                                fieldWithPath("role").description("요청 사용자 역할 (OWNER, MEMBER, GUEST)")
                        )
                ));

        verify(groupService, times(1)).menu(1L, groupId);
    }

    @Test
    @DisplayName("비로그인 사용자는 그룹 메뉴 조회 시 GUEST 역할을 전달받는다.")
    void 비로그인_사용자는_그룹_메뉴_조회_시_GUEST_역할을_전달받는다() throws Exception {
        // given
        Long groupId = 20L;
        GroupMenuResponseDto response = new GroupMenuResponseDto(
                "제주 모임",
                "게스트 조회",
                2,
                10,
                "https://example.com/guest-thumb.png",
                Role.GUEST
        );
        given(groupService.menu(isNull(), eq(groupId))).willReturn(response);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/menu", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thumbNailUrl").value("https://example.com/guest-thumb.png"))
                .andExpect(jsonPath("$.role").value("GUEST"));

        verify(groupService, times(1)).menu(isNull(), eq(groupId));
    }

    @Test
    @DisplayName("비멤버 사용자가 비공개 그룹 메뉴를 조회하면 403을 반환한다.")
    void 비멤버_사용자가_비공개_그룹_메뉴를_조회하면_403을_반환한다() throws Exception {
        Long groupId = 20L;
        given(groupService.menu(isNull(), eq(groupId)))
                .willThrow(new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER));

        mockMvc.perform(get("/groups/{groupId}/menu", groupId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹을 조회할 권한이 없습니다."))
                .andDo(document("groups/menu-fail-not-group-member",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("메뉴 정보를 조회할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, times(1)).menu(isNull(), eq(groupId));
    }

    @Test
    @DisplayName("그룹 멤버 목록을 조회하면 멤버 정보를 반환한다.")
    void 그룹_멤버_목록을_조회하면_멤버_정보를_반환한다() throws Exception {
        // given
        Long groupId = 10L;
        String ownerPublicUuid = UUID.randomUUID().toString();
        String memberPublicUuid = UUID.randomUUID().toString();
        GroupUsersResponseDto response = new GroupUsersResponseDto(
                List.of(
                        new GroupUsersResponseDto.UserDto(ownerPublicUuid, "상윤", "모임장", "http://img-owner", true),
                        new GroupUsersResponseDto.UserDto(memberPublicUuid, "민규", "멤버", "http://img-member", false)
                )
        );
        given(groupService.groupUsers(eq(groupId))).willReturn(response);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/users", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].id").value("enc-" + ownerPublicUuid))
                .andExpect(jsonPath("$.users[0].name").value("상윤"))
                .andExpect(jsonPath("$.users[0].description").value("모임장"))
                .andExpect(jsonPath("$.users[0].profileUrl").value("http://img-owner"))
                .andExpect(jsonPath("$.users[0].isOwner").value(true))
                .andExpect(jsonPath("$.users[1].id").value("enc-" + memberPublicUuid))
                .andExpect(jsonPath("$.users[1].name").value("민규"))
                .andExpect(jsonPath("$.users[1].isOwner").value(false))
                .andDo(document("groups/users",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("멤버 목록을 조회할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("users").description("그룹 멤버 목록"),
                                fieldWithPath("users[].id").description("암호화된 멤버 공개 UUID"),
                                fieldWithPath("users[].name").description("멤버 이름"),
                                fieldWithPath("users[].description").description("멤버 소개").optional(),
                                fieldWithPath("users[].profileUrl").description("멤버 프로필 이미지 URL").optional(),
                                fieldWithPath("users[].isOwner").description("방장 여부")
                        )
                ));

        verify(groupService, times(1)).groupUsers(groupId);
    }

    @Test
    @DisplayName("존재하지 않는 그룹의 멤버 목록 조회 시 404를 반환한다.")
    void 존재하지_않는_그룹의_멤버_목록_조회_시_404를_반환한다() throws Exception {
        // given
        Long groupId = 999L;
        given(groupService.groupUsers(eq(groupId)))
                .willThrow(new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/groups/{groupId}/users", groupId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 그룹 입니다."))
                .andDo(document("groups/users-fail-group-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("멤버 목록을 조회할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, times(1)).groupUsers(groupId);
    }

    @Test
    @DisplayName("비로그인 사용자가 비공개 그룹 멤버 목록 조회 시 403을 반환한다.")
    void 비로그인_사용자가_비공개_그룹_멤버_목록_조회_시_403을_반환한다() throws Exception {
        // given
        Long groupId = 20L;
        given(groupService.groupUsers(eq(groupId)))
                .willThrow(new BusinessException(GroupErrorCode.CANNOT_GET_PRIVATE_GROUP_MEMBERS));

        // when & then
        mockMvc.perform(get("/groups/{groupId}/users", groupId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("PRIVATE 그룹 멤버 목록은 조회할 수 없습니다."))
                .andDo(document("groups/users-fail-not-group-member",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("멤버 목록을 조회할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, times(1)).groupUsers(groupId);
    }

    @Test
    @DisplayName("비로그인 사용자는 공개 그룹 상세 정보를 조회할 수 있다.")
    void 비로그인_사용자는_공개_그룹_상세_정보를_조회할_수_있다() throws Exception {
        // given
        Long groupId = 1L;
        GroupDetailResponseDto response = new GroupDetailResponseDto(
                List.of(
                        new GroupDetailResponseDto.UserDto("상윤", "모임장", "http://img", true),
                        new GroupDetailResponseDto.UserDto("민규", "멤버", "http://img2", false)
                ),
                "여행모임",
                "3월 일본 여행",
                GroupKind.PUBLIC,
                "https://example.com/thumb.png",
                2,
                10,
                Role.GUEST,
                List.of(new GroupDetailResponseDto.RecentPhotoDto(100L, "https://example.com/review-image-1.png")),
                List.of(new GroupDetailResponseDto.RecentTravelDto(
                        200L,
                        "벚꽃여행",
                        "https://example.com/travel-thumb-1.png",
                        "교토 벚꽃 시즌 여행",
                        2,
                        LocalDateTime.of(2026, 3, 20, 9, 0),
                        LocalDateTime.of(2026, 3, 22, 18, 0)
                )),
                List.of(new GroupDetailResponseDto.RecentReviewDto(
                        300L,
                        "벚꽃여행",
                        "즐거운 여행이었어요",
                        "민규",
                        "https://example.com/review-image-2.png",
                        100,
                        LocalDateTime.of(2026, 3, 21, 10, 0)
                ))
        );

        given(groupService.detail(eq(groupId), eq(null)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/groups/{groupId}", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].name").value("상윤"))
                .andExpect(jsonPath("$.users[0].isOwner").value(true))
                .andExpect(jsonPath("$.name").value("여행모임"))
                .andExpect(jsonPath("$.description").value("3월 일본 여행"))
                .andExpect(jsonPath("$.groupKind").value("PUBLIC"))
                .andExpect(jsonPath("$.thumbNailUrl").value("https://example.com/thumb.png"))
                .andExpect(jsonPath("$.currentMemberCount").value(2))
                .andExpect(jsonPath("$.memberLimit").value(10))
                .andExpect(jsonPath("$.role").value("GUEST"))
                .andExpect(jsonPath("$.recentPhotos.length()").value(1))
                .andExpect(jsonPath("$.recentTravels.length()").value(1))
                .andExpect(jsonPath("$.recentTravels[0].travelItineraryId").value(200))
                .andExpect(jsonPath("$.recentTravels[0].title").value("벚꽃여행"))
                .andExpect(jsonPath("$.recentTravels[0].thumbnailUrl").value("https://example.com/travel-thumb-1.png"))
                .andExpect(jsonPath("$.recentTravels[0].description").value("교토 벚꽃 시즌 여행"))
                .andExpect(jsonPath("$.recentTravels[0].memberCount").value(2))
                .andExpect(jsonPath("$.recentTravels[0].startAt").value("2026-03-20T09:00:00"))
                .andExpect(jsonPath("$.recentTravels[0].endAt").value("2026-03-22T18:00:00"))
                .andExpect(jsonPath("$.recentReviews.length()").value(1))
                .andExpect(jsonPath("$.recentReviews[0].travelItineraryName").value("벚꽃여행"))
                .andExpect(jsonPath("$.recentReviews[0].view").value(100))
                .andExpect(jsonPath("$.recentReviews[0].createdAt").value("2026-03-21T10:00:00"))
                .andExpect(jsonPath("$.recentReviews[0].imageUrl").value("https://example.com/review-image-2.png"))
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
                                fieldWithPath("memberLimit").description("최대 인원"),
                                fieldWithPath("role").description("요청한 사용자의 그룹 내 역할 (OWNER, MEMBER, GUEST)").optional(),
                                fieldWithPath("recentPhotos").description("최근 사진 최대 4개"),
                                fieldWithPath("recentPhotos[].imageId").description("사진 ID"),
                                fieldWithPath("recentPhotos[].imageUrl").description("사진 URL"),
                                fieldWithPath("recentTravels").description("최근 여행 일정 최대 4개"),
                                fieldWithPath("recentTravels[].travelItineraryId").description("여행 일정 ID"),
                                fieldWithPath("recentTravels[].title").description("여행 일정 제목"),
                                fieldWithPath("recentTravels[].thumbnailUrl").description("여행 일정 썸네일 URL").optional(),
                                fieldWithPath("recentTravels[].description").description("여행 일정 설명").optional(),
                                fieldWithPath("recentTravels[].memberCount").description("현재 참여 인원"),
                                fieldWithPath("recentTravels[].startAt").description("여행 시작 일시"),
                                fieldWithPath("recentTravels[].endAt").description("여행 종료 일시"),
                                fieldWithPath("recentReviews").description("최근 여행 후기 최대 4개"),
                                fieldWithPath("recentReviews[].reviewId").description("여행 후기 ID"),
                                fieldWithPath("recentReviews[].travelItineraryName").description("여행 일정 이름"),
                                fieldWithPath("recentReviews[].content").description("여행 후기 내용"),
                                fieldWithPath("recentReviews[].writerNickname").description("작성자 닉네임"),
                                fieldWithPath("recentReviews[].imageUrl").description("여행 후기 대표 이미지 URL").optional(),
                                fieldWithPath("recentReviews[].view").description("여행 후기 조회수"),
                                fieldWithPath("recentReviews[].createdAt").description("여행 후기 작성 일시")
                        )
                ));

        verify(groupService, times(1)).detail(groupId, null);
    }

    @Test
    @DisplayName("존재하지 않는 그룹 상세 조회 시 404를 반환한다.")
    void 존재하지_않는_그룹_상세_조회_시_404를_반환한다() throws Exception {
        Long groupId = 999L;
        given(groupService.detail(eq(groupId), eq(null)))
                .willThrow(new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        mockMvc.perform(get("/groups/{groupId}", groupId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 그룹 입니다."))
                .andDo(document("groups/detail-fail-group-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("조회할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, times(1)).detail(groupId, null);
    }

    @Test
    @DisplayName("비멤버 사용자가 비공개 그룹 상세 조회 시 403을 반환한다.")
    void 비멤버_사용자가_비공개_그룹_상세_조회_시_403을_반환한다() throws Exception {
        Long groupId = 1L;
        given(groupService.detail(eq(groupId), eq(null)))
                .willThrow(new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER));

        mockMvc.perform(get("/groups/{groupId}", groupId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹을 조회할 권한이 없습니다."))
                .andDo(document("groups/detail-fail-not-group-member",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("조회할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, times(1)).detail(groupId, null);
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
    @DisplayName("비로그인 사용자가 그룹 삭제 요청 시 401을 반환한다.")
    void 비로그인_사용자가_그룹_삭제_요청_시_401을_반환한다() throws Exception {
        mockMvc.perform(delete("/groups/{groupId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("groups/delete-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("삭제할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, never()).delete(any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("오너가 아닌 사용자가 그룹 삭제 요청 시 403을 반환한다.")
    void 오너가_아닌_사용자가_그룹_삭제_요청_시_403을_반환한다() throws Exception {
        Long groupId = 1L;
        mockCsrfValid();
        doThrow(new BusinessException(GroupErrorCode.NOT_GROUP_OWNER))
                .when(groupService).delete(groupId, 1L);

        mockMvc.perform(delete("/groups/{groupId}", groupId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("그룹 수정/삭제 권한이 없습니다."))
                .andDo(document("groups/delete-fail-not-group-owner",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("삭제할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, times(1)).delete(groupId, 1L);
    }

    @Test
    @DisplayName("존재하지 않는 그룹 삭제 요청 시 404를 반환한다.")
    void 존재하지_않는_그룹_삭제_요청_시_404를_반환한다() throws Exception {
        Long groupId = 999L;
        mockCsrfValid();
        doThrow(new BusinessException(GroupErrorCode.GROUP_NOT_FOUND))
                .when(groupService).delete(groupId, 1L);

        mockMvc.perform(delete("/groups/{groupId}", groupId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 그룹 입니다."))
                .andDo(document("groups/delete-fail-group-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("삭제할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, times(1)).delete(groupId, 1L);
    }

    @Test
    @DisplayName("그룹에 다른 멤버가 남아있으면 그룹 삭제 요청 시 409를 반환한다.")
    void 그룹에_다른_멤버가_남아있으면_그룹_삭제_요청_시_409를_반환한다() throws Exception {
        Long groupId = 1L;
        mockCsrfValid();
        doThrow(new BusinessException(GroupErrorCode.CANNOT_DELETE_GROUP_WITH_MEMBERS))
                .when(groupService).delete(groupId, 1L);

        mockMvc.perform(delete("/groups/{groupId}", groupId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("그룹에 다른 멤버가 있어 삭제할 수 없습니다."))
                .andDo(document("groups/delete-fail-cannot-delete-with-members",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("삭제할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, times(1)).delete(groupId, 1L);
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
    @DisplayName("비로그인 사용자가 그룹 수정 요청 시 401을 반환한다.")
    void 비로그인_사용자가_그룹_수정_요청_시_401을_반환한다() throws Exception {
        String body = """
                {
                  "groupKind": "PRIVATE",
                  "name": "수정모임",
                  "description": "수정설명",
                  "thumbNailUrl": "https://example.com/updated.png",
                  "memberLimit": 20
                }
                """;

        mockMvc.perform(patch("/groups/{groupId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("groups/update-fail-unauthorized",
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
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(groupService, never()).update(any(GroupUpdateRequestDto.class), any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("오너가 아닌 사용자가 그룹 수정 요청 시 403을 반환한다.")
    void 오너가_아닌_사용자가_그룹_수정_요청_시_403을_반환한다() throws Exception {
        Long groupId = 1L;
        mockCsrfValid();
        given(groupService.update(any(GroupUpdateRequestDto.class), eq(groupId), eq(1L)))
                .willThrow(new BusinessException(GroupErrorCode.NOT_GROUP_OWNER));

        String body = """
                {
                  "groupKind": "PRIVATE",
                  "name": "수정모임",
                  "description": "수정설명",
                  "thumbNailUrl": "https://example.com/updated.png",
                  "memberLimit": 20
                }
                """;

        mockMvc.perform(patch("/groups/{groupId}", groupId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("그룹 수정/삭제 권한이 없습니다."))
                .andDo(document("groups/update-fail-not-group-owner",
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
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("존재하지 않는 그룹 수정 요청 시 404를 반환한다.")
    void 존재하지_않는_그룹_수정_요청_시_404를_반환한다() throws Exception {
        Long groupId = 999L;
        mockCsrfValid();
        given(groupService.update(any(GroupUpdateRequestDto.class), eq(groupId), eq(1L)))
                .willThrow(new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));

        String body = """
                {
                  "groupKind": "PRIVATE",
                  "name": "수정모임",
                  "description": "수정설명",
                  "thumbNailUrl": "https://example.com/updated.png",
                  "memberLimit": 20
                }
                """;

        mockMvc.perform(patch("/groups/{groupId}", groupId)
                        .with(loginSessionAndCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 그룹 입니다."))
                .andDo(document("groups/update-fail-group-not-found",
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
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("그룹 소유자는 멤버를 추방할 수 있다.")
    void 그룹_소유자는_멤버를_추방할_수_있다() throws Exception {
        // given
        Long groupId = 1L;
        Long ownerId = 1L;
        String targetUserId = "00000000-0000-0000-0000-000000000002";
        doNothing().when(groupService).kick(groupId, ownerId, targetUserId);
        mockCsrfValid();

        // when & then
        mockMvc.perform(delete("/groups/{groupId}/users/{targetUserId}", groupId, targetUserId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("groups/kick",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("멤버를 추방할 그룹 ID"),
                                parameterWithName("targetUserId").description("추방할 사용자 ID")
                        )
                ));

        verify(groupService, times(1)).kick(groupId, ownerId, targetUserId);
        verify(csrfTokenManager, times(1)).isValid(any(HttpServletRequest.class), any(String.class));
    }

    @Test
    @DisplayName("오너가 아닌 사용자가 멤버 추방 요청 시 403을 반환한다.")
    void 오너가_아닌_사용자가_멤버_추방_요청_시_403을_반환한다() throws Exception {
        Long groupId = 1L;
        String targetUserId = "00000000-0000-0000-0000-000000000002";
        mockCsrfValid();
        doThrow(new BusinessException(GroupErrorCode.NOT_GROUP_OWNER))
                .when(groupService).kick(groupId, 1L, targetUserId);

        mockMvc.perform(delete("/groups/{groupId}/users/{targetUserId}", groupId, targetUserId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("그룹 수정/삭제 권한이 없습니다."))
                .andDo(document("groups/kick-fail-not-group-owner",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("멤버를 추방할 그룹 ID"),
                                parameterWithName("targetUserId").description("추방할 사용자 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("자기 자신을 추방하려고 하면 403을 반환한다.")
    void 자기_자신을_추방하려고_하면_403을_반환한다() throws Exception {
        Long groupId = 1L;
        String targetUserId = "00000000-0000-0000-0000-000000000001";
        mockCsrfValid();
        doThrow(new BusinessException(GroupErrorCode.CANNOT_KICK_SELF))
                .when(groupService).kick(groupId, 1L, targetUserId);

        mockMvc.perform(delete("/groups/{groupId}/users/{targetUserId}", groupId, targetUserId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자기 자신은 추방할 수 없습니다."))
                .andDo(document("groups/kick-fail-cannot-kick-self",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("멤버를 추방할 그룹 ID"),
                                parameterWithName("targetUserId").description("추방할 사용자 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("로그인한 멤버는 그룹을 탈퇴할 수 있다.")
    void 로그인한_멤버는_그룹을_탈퇴할_수_있다() throws Exception {
        // given
        Long groupId = 1L;
        Long userId = 1L;
        doNothing().when(groupService).leave(groupId, userId);
        mockCsrfValid();

        // when & then
        mockMvc.perform(delete("/groups/{groupId}/users/me", groupId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("groups/leave",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("탈퇴할 그룹 ID")
                        )
                ));

        verify(groupService, times(1)).leave(groupId, userId);
        verify(csrfTokenManager, times(1)).isValid(any(HttpServletRequest.class), any(String.class));
    }

    @Test
    @DisplayName("그룹 소유자가 탈퇴 요청 시 403을 반환한다.")
    void 그룹_소유자가_탈퇴_요청_시_403을_반환한다() throws Exception {
        Long groupId = 1L;
        mockCsrfValid();
        doThrow(new BusinessException(GroupErrorCode.GROUP_OWNER_NOT_LEAVE))
                .when(groupService).leave(groupId, 1L);

        mockMvc.perform(delete("/groups/{groupId}/users/me", groupId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("그룹 주인은 탈퇴할 수 없습니다."))
                .andDo(document("groups/leave-fail-owner-not-leave",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("탈퇴할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("이미 탈퇴한 그룹에 대해 탈퇴 요청 시 403을 반환한다.")
    void 이미_탈퇴한_그룹에_대해_탈퇴_요청_시_403을_반환한다() throws Exception {
        Long groupId = 1L;
        mockCsrfValid();
        doThrow(new BusinessException(GroupErrorCode.ALREADY_LEAVE_GROUP))
                .when(groupService).leave(groupId, 1L);

        mockMvc.perform(delete("/groups/{groupId}/users/me", groupId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이미 탈퇴한 그룹입니다."))
                .andDo(document("groups/leave-fail-already-left",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("탈퇴할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("그룹 소유권을 이전한다.")
    void 그룹_소유권을_이전한다() throws Exception {
        // given
        Long groupId = 1L;
        Long ownerId = 1L;
        String targetUserId = "00000000-0000-0000-0000-000000000002";
        doNothing().when(groupService).ownerTransfer(groupId, targetUserId, ownerId);
        mockCsrfValid();

        // when & then
        mockMvc.perform(patch("/groups/{groupId}/owner/{targetUserId}", groupId, targetUserId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("groups/owner-transfer",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("소유권을 이전할 그룹 ID"),
                                parameterWithName("targetUserId").description("새로운 소유자가 될 사용자 ID")
                        )
                ));

        verify(groupService, times(1)).ownerTransfer(groupId, targetUserId, ownerId);
        verify(csrfTokenManager, times(1)).isValid(any(HttpServletRequest.class), any(String.class));
    }

    @Test
    @DisplayName("자기 자신에게 소유권 이전 요청 시 403을 반환한다.")
    void 자기_자신에게_소유권_이전_요청_시_403을_반환한다() throws Exception {
        Long groupId = 1L;
        String targetUserId = "00000000-0000-0000-0000-000000000001";
        mockCsrfValid();
        doThrow(new BusinessException(GroupErrorCode.CANNOT_OWNER_DEMOTE_SELF))
                .when(groupService).ownerTransfer(groupId, targetUserId, 1L);

        mockMvc.perform(patch("/groups/{groupId}/owner/{targetUserId}", groupId, targetUserId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("그룹 주인은 스스로를 강등시킬 수 없습니다."))
                .andDo(document("groups/owner-transfer-fail-cannot-demote-self",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("소유권을 이전할 그룹 ID"),
                                parameterWithName("targetUserId").description("새로운 소유자가 될 사용자 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("오너가 아닌 사용자의 소유권 이전 요청 시 403을 반환한다.")
    void 오너가_아닌_사용자의_소유권_이전_요청_시_403을_반환한다() throws Exception {
        Long groupId = 1L;
        String targetUserId = "00000000-0000-0000-0000-000000000002";
        mockCsrfValid();
        doThrow(new BusinessException(GroupErrorCode.NOT_GROUP_OWNER))
                .when(groupService).ownerTransfer(groupId, targetUserId, 1L);

        mockMvc.perform(patch("/groups/{groupId}/owner/{targetUserId}", groupId, targetUserId)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("그룹 수정/삭제 권한이 없습니다."))
                .andDo(document("groups/owner-transfer-fail-not-group-owner",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("소유권을 이전할 그룹 ID"),
                                parameterWithName("targetUserId").description("새로운 소유자가 될 사용자 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("그룹 이름은 필수입니다."))
                .andDo(document("groups/update-fail-bad-request",
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
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

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
