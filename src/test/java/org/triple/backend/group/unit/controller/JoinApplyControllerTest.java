package org.triple.backend.group.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.controller.JoinApplyController;
import org.triple.backend.group.dto.response.JoinApplyUserResponseDto;
import org.triple.backend.group.entity.joinApply.JoinApplyStatus;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.exception.JoinApplyErrorCode;
import org.triple.backend.group.service.JoinApplyService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN;
import static org.triple.backend.global.constants.AuthConstants.CSRF_TOKEN_KEY;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@WebMvcTest(JoinApplyController.class)
public class JoinApplyControllerTest extends ControllerTest {

    @MockitoBean
    private JoinApplyService joinApplyService;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @Test
    @DisplayName("로그인 사용자는 그룹 가입 신청을 할 수 있다")
    void 로그인_사용자는_그룹_가입_신청을_할_수_있다() throws Exception {
        // given
        mockCsrfValid();

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies", 1L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("groups/join-apply",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청할 그룹 ID")
                        )
                ));

        verify(joinApplyService, times(1)).joinApply(1L, 1L);
        verify(csrfTokenManager, times(1)).isValid(any(HttpServletRequest.class), any(String.class));
    }

    @Test
    @DisplayName("비로그인 사용자는 그룹 가입 신청 시 401을 반환한다")
    void 비로그인_사용자는_그룹_가입_신청_시_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("groups/join-apply-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, never()).joinApply(any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("로그인 사용자의 CSRF 토큰이 유효하지 않으면 그룹 가입 신청 요청은 403을 반환한다")
    void 로그인_사용자의_CSRF_토큰이_유효하지_않으면_그룹_가입_신청_요청은_403을_반환한다() throws Exception {
        when(csrfTokenManager.isValid(any(HttpServletRequest.class), any(String.class))).thenReturn(false);

        mockMvc.perform(post("/groups/{groupId}/join-applies", 1L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF 토큰이 유효하지 않습니다."))
                .andDo(document("groups/join-apply-fail-invalid-csrf-token",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, never()).joinApply(any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("이미 가입 신청된 그룹에 가입 신청 요청 시 409를 반환한다")
    void 이미_가입_신청된_그룹에_가입_신청_요청_시_409를_반환한다() throws Exception {
        mockCsrfValid();
        doThrow(new BusinessException(JoinApplyErrorCode.ALREADY_APPLY_JOIN_REQUEST))
                .when(joinApplyService).joinApply(1L, 1L);

        mockMvc.perform(post("/groups/{groupId}/join-applies", 1L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 가입이 요청된 그룹입니다."))
                .andDo(document("groups/join-apply-fail-already-applied",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, times(1)).joinApply(1L, 1L);
    }

    @Test
    @DisplayName("존재하지 않는 그룹 가입 신청 요청 시 404를 반환한다")
    void 존재하지_않는_그룹_가입_신청_요청_시_404를_반환한다() throws Exception {
        mockCsrfValid();
        doThrow(new BusinessException(GroupErrorCode.GROUP_NOT_FOUND))
                .when(joinApplyService).joinApply(1L, 1L);

        mockMvc.perform(post("/groups/{groupId}/join-applies", 1L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 그룹 입니다."))
                .andDo(document("groups/join-apply-fail-group-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청할 그룹 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, times(1)).joinApply(1L, 1L);
    }

    @Test
    @DisplayName("로그인 사용자는 그룹 가입 신청을 승인할 수 있다")
    void 로그인_사용자는_그룹_가입_신청을_승인할_수_있다() throws Exception {
        // given
        mockCsrfValid();

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}", 1L, 2L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andDo(document("groups/join-apply-approve",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청을 승인할 그룹 ID"),
                                parameterWithName("joinApplyId").description("승인할 가입 신청 ID")
                        )
                ));

        verify(joinApplyService, times(1)).approve(1L, 1L, 2L);
        verify(csrfTokenManager, times(1)).isValid(any(HttpServletRequest.class), any(String.class));
    }

    @Test
    @DisplayName("이미 가입된 사용자를 승인하면 409를 반환한다")
    void 이미_가입된_사용자를_승인하면_409를_반환한다() throws Exception {
        // given
        mockCsrfValid();
        doThrow(new BusinessException(JoinApplyErrorCode.ALREADY_JOINED_GROUP))
                .when(joinApplyService).approve(1L, 1L, 2L);

        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}", 1L, 2L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 가입된 그룹입니다."))
                .andDo(document("groups/join-apply-approve-fail-already-joined",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청을 승인할 그룹 ID"),
                                parameterWithName("joinApplyId").description("승인할 가입 신청 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("비로그인 사용자는 그룹 가입 승인 시 401을 반환한다")
    void 비로그인_사용자는_그룹_가입_승인_시_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}", 1L, 2L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("groups/join-apply-approve-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청을 승인할 그룹 ID"),
                                parameterWithName("joinApplyId").description("승인할 가입 신청 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, never()).approve(any(Long.class), any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("로그인 사용자의 CSRF 토큰이 유효하지 않으면 그룹 가입 승인 요청은 403을 반환한다")
    void 로그인_사용자의_CSRF_토큰이_유효하지_않으면_그룹_가입_승인_요청은_403을_반환한다() throws Exception {
        when(csrfTokenManager.isValid(any(HttpServletRequest.class), any(String.class))).thenReturn(false);

        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}", 1L, 2L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF 토큰이 유효하지 않습니다."))
                .andDo(document("groups/join-apply-approve-fail-invalid-csrf-token",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청을 승인할 그룹 ID"),
                                parameterWithName("joinApplyId").description("승인할 가입 신청 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, never()).approve(any(Long.class), any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("오너가 아닌 사용자의 그룹 가입 승인 요청 시 403을 반환한다")
    void 오너가_아닌_사용자의_그룹_가입_승인_요청_시_403을_반환한다() throws Exception {
        mockCsrfValid();
        doThrow(new BusinessException(GroupErrorCode.NOT_GROUP_OWNER))
                .when(joinApplyService).approve(1L, 1L, 2L);

        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}", 1L, 2L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("그룹 수정/삭제 권한이 없습니다."))
                .andDo(document("groups/join-apply-approve-fail-not-group-owner",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청을 승인할 그룹 ID"),
                                parameterWithName("joinApplyId").description("승인할 가입 신청 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, times(1)).approve(1L, 1L, 2L);
    }

    @Test
    @DisplayName("존재하지 않는 가입 신청 승인 요청 시 404를 반환한다")
    void 존재하지_않는_가입_신청_승인_요청_시_404를_반환한다() throws Exception {
        mockCsrfValid();
        doThrow(new BusinessException(JoinApplyErrorCode.JOIN_APPLY_NOT_FOUND))
                .when(joinApplyService).approve(1L, 1L, 2L);

        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}", 1L, 2L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 가입 신청입니다."))
                .andDo(document("groups/join-apply-approve-fail-join-apply-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청을 승인할 그룹 ID"),
                                parameterWithName("joinApplyId").description("승인할 가입 신청 ID")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, times(1)).approve(1L, 1L, 2L);
    }

    @Test
    @DisplayName("로그인 사용자는 상태별 가입 신청 사용자 목록을 조회할 수 있다")
    void 로그인_사용자는_상태별_가입_신청_사용자_목록을_조회할_수_있다() throws Exception {
        // given
        JoinApplyUserResponseDto response = new JoinApplyUserResponseDto(
                List.of(
                        new JoinApplyUserResponseDto.UserDto(101L, "지원자1", "자기소개1", "https://example.com/1.png", JoinApplyStatus.PENDING),
                        new JoinApplyUserResponseDto.UserDto(100L, "지원자2", "자기소개2", "https://example.com/2.png", JoinApplyStatus.PENDING)
                ),
                98L,
                true
        );
        when(joinApplyService.joinApplyUser(1L, 1L, JoinApplyStatus.PENDING, null, 10)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/join-applies", 1L)
                        .param("status", "PENDING")
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].joinApplyId").value(101L))
                .andExpect(jsonPath("$.users[0].nickname").value("지원자1"))
                .andExpect(jsonPath("$.users[0].status").value("PENDING"))
                .andExpect(jsonPath("$.nextCursor").value(98L))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andDo(document("groups/join-apply-users",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청 내역을 조회할 그룹 ID")
                        ),
                        queryParameters(
                                parameterWithName("status").optional().description("조회할 가입 신청 상태(PENDING, APPROVED, REJECTED, CANCELED). 미입력 시 전체 상태 조회"),
                                parameterWithName("cursor").optional().description("커서(다음 페이지 조회 시 사용). 첫 페이지는 생략"),
                                parameterWithName("size").optional().description("페이지 크기(기본 10)")
                        ),
                        responseFields(
                                fieldWithPath("users").description("가입 신청 사용자 목록"),
                                fieldWithPath("users[].joinApplyId").description("가입 신청 ID"),
                                fieldWithPath("users[].nickname").description("닉네임"),
                                fieldWithPath("users[].description").description("자기소개").optional(),
                                fieldWithPath("users[].profileUrl").description("프로필 이미지 URL").optional(),
                                fieldWithPath("users[].status").description("가입 신청 상태"),
                                fieldWithPath("nextCursor").description("다음 페이지 커서 (없으면 null)").optional(),
                                fieldWithPath("hasNext").description("다음 페이지 존재 여부")
                        )
                ));

        verify(joinApplyService, times(1)).joinApplyUser(1L, 1L, JoinApplyStatus.PENDING, null, 10);
    }

    @Test
    @DisplayName("status 미입력 시 전체 가입 신청 사용자 목록을 조회할 수 있다")
    void status_미입력_시_전체_가입_신청_사용자_목록을_조회할_수_있다() throws Exception {
        // given
        JoinApplyUserResponseDto response = new JoinApplyUserResponseDto(
                List.of(
                        new JoinApplyUserResponseDto.UserDto(101L, "지원자1", "자기소개1", "https://example.com/1.png", JoinApplyStatus.PENDING),
                        new JoinApplyUserResponseDto.UserDto(100L, "지원자2", "자기소개2", "https://example.com/2.png", JoinApplyStatus.APPROVED)
                ),
                null,
                false
        );
        when(joinApplyService.joinApplyUser(1L, 1L, null, null, 10)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/join-applies", 1L)
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].joinApplyId").value(101L))
                .andExpect(jsonPath("$.users[0].status").value("PENDING"))
                .andExpect(jsonPath("$.users[1].status").value("APPROVED"))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(joinApplyService, times(1)).joinApplyUser(1L, 1L, null, null, 10);
    }

    @Test
    @DisplayName("cursor와 size를 지정해 다음 페이지 가입 신청 사용자 목록을 조회할 수 있다")
    void cursor와_size를_지정해_다음_페이지_가입_신청_사용자_목록을_조회할_수_있다() throws Exception {
        // given
        JoinApplyUserResponseDto response = new JoinApplyUserResponseDto(
                List.of(
                        new JoinApplyUserResponseDto.UserDto(97L, "지원자3", "자기소개3", "https://example.com/3.png", JoinApplyStatus.PENDING),
                        new JoinApplyUserResponseDto.UserDto(96L, "지원자2", "자기소개2", "https://example.com/2.png", JoinApplyStatus.PENDING)
                ),
                95L,
                true
        );
        when(joinApplyService.joinApplyUser(1L, 1L, JoinApplyStatus.PENDING, 97L, 2)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/join-applies", 1L)
                        .param("status", "PENDING")
                        .param("cursor", "97")
                        .param("size", "2")
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].joinApplyId").value(97L))
                .andExpect(jsonPath("$.users[0].nickname").value("지원자3"))
                .andExpect(jsonPath("$.nextCursor").value(95L))
                .andExpect(jsonPath("$.hasNext").value(true));

        verify(joinApplyService, times(1)).joinApplyUser(1L, 1L, JoinApplyStatus.PENDING, 97L, 2);
    }

    @Test
    @DisplayName("비로그인 사용자는 가입 신청 사용자 목록 조회 시 401을 반환한다")
    void 비로그인_사용자는_가입_신청_사용자_목록_조회_시_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/groups/{groupId}/join-applies", 1L)
                        .param("status", "PENDING"))
                .andExpect(status().isUnauthorized())
                .andDo(document("groups/join-apply-users-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청 내역을 조회할 그룹 ID")
                        ),
                        queryParameters(
                                parameterWithName("status").optional().description("조회할 가입 신청 상태(PENDING, APPROVED, REJECTED, CANCELED). 미입력 시 전체 상태 조회"),
                                parameterWithName("cursor").optional().description("커서(다음 페이지 조회 시 사용). 첫 페이지는 생략"),
                                parameterWithName("size").optional().description("페이지 크기(기본 10)")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, never()).joinApplyUser(
                any(Long.class),
                any(Long.class),
                any(JoinApplyStatus.class),
                any(Long.class),
                anyInt()
        );
    }

    @Test
    @DisplayName("오너가 아닌 사용자가 가입 신청 사용자 목록 조회 시 403을 반환한다")
    void 오너가_아닌_사용자가_가입_신청_사용자_목록_조회_시_403을_반환한다() throws Exception {
        // given
        doThrow(new BusinessException(GroupErrorCode.NOT_GROUP_OWNER))
                .when(joinApplyService).joinApplyUser(1L, 1L, JoinApplyStatus.PENDING, null, 10);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/join-applies", 1L)
                        .param("status", "PENDING")
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isForbidden())
                .andDo(document("groups/join-apply-users-fail-not-group-owner",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청 내역을 조회할 그룹 ID")
                        ),
                        queryParameters(
                                parameterWithName("status").optional().description("조회할 가입 신청 상태(PENDING, APPROVED, REJECTED, CANCELED). 미입력 시 전체 상태 조회"),
                                parameterWithName("cursor").optional().description("커서(다음 페이지 조회 시 사용). 첫 페이지는 생략"),
                                parameterWithName("size").optional().description("페이지 크기(기본 10)")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, times(1)).joinApplyUser(1L, 1L, JoinApplyStatus.PENDING, null, 10);
    }

    @Test
    @DisplayName("존재하지 않는 그룹의 가입 신청 사용자 목록 조회 시 404를 반환한다")
    void 존재하지_않는_그룹의_가입_신청_사용자_목록_조회_시_404를_반환한다() throws Exception {
        // given
        doThrow(new BusinessException(GroupErrorCode.GROUP_NOT_FOUND))
                .when(joinApplyService).joinApplyUser(1L, 1L, JoinApplyStatus.PENDING, null, 10);

        // when & then
        mockMvc.perform(get("/groups/{groupId}/join-applies", 1L)
                        .param("status", "PENDING")
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isNotFound())
                .andDo(document("groups/join-apply-users-fail-group-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청 내역을 조회할 그룹 ID")
                        ),
                        queryParameters(
                                parameterWithName("status").optional().description("조회할 가입 신청 상태(PENDING, APPROVED, REJECTED, CANCELED). 미입력 시 전체 상태 조회"),
                                parameterWithName("cursor").optional().description("커서(다음 페이지 조회 시 사용). 첫 페이지는 생략"),
                                parameterWithName("size").optional().description("페이지 크기(기본 10)")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, times(1)).joinApplyUser(1L, 1L, JoinApplyStatus.PENDING, null, 10);
    }

    @Test
    @DisplayName("잘못된 status 값으로 가입 신청 사용자 목록 조회 시 400을 반환한다")
    void 잘못된_status_값으로_가입_신청_사용자_목록_조회_시_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/groups/{groupId}/join-applies", 1L)
                        .param("status", "INVALID_STATUS")
                        .with(loginSessionAndCsrf()))
                .andExpect(status().isBadRequest())
                .andDo(document("groups/join-apply-users-fail-invalid-status",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                                parameterWithName("groupId").description("가입 신청 내역을 조회할 그룹 ID")
                        ),
                        queryParameters(
                                parameterWithName("status").description("조회할 가입 신청 상태(PENDING, APPROVED, REJECTED, CANCELED)"),
                                parameterWithName("cursor").optional().description("커서(다음 페이지 조회 시 사용). 첫 페이지는 생략"),
                                parameterWithName("size").optional().description("페이지 크기(기본 10)")
                        ),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )
                ));

        verify(joinApplyService, never()).joinApplyUser(
                any(Long.class),
                any(Long.class),
                any(JoinApplyStatus.class),
                any(Long.class),
                anyInt()
        );
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
