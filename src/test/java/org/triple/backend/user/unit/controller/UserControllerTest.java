package org.triple.backend.user.unit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.triple.backend.auth.exception.AuthErrorCode;
import org.triple.backend.auth.session.SessionManager;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.user.controller.UserController;
import org.triple.backend.user.dto.response.UserInfoResponseDto;
import org.triple.backend.user.service.UserService;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@WebMvcTest(UserController.class)
public class UserControllerTest extends ControllerTest{

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CsrfTokenManager csrfTokenManager;

    @MockitoBean
    private SessionManager sessionManager;

    @Test
    @DisplayName("사용자 정보 조회를 하면 사용자 정보와 상태코드 200을 반환한다.")
    void 사용자_정보_조회를_하면_사용자_정보와_상태코드_200을_반환한다() throws Exception {
        // given
        Long userId = 1L;
        String encryptedPublicUuid = "encrypted-public-uuid-3";

        // when
        when(userService.userInfo(userId))
                .thenReturn(UserInfoResponseDto.builder()
                        .publicUuid(encryptedPublicUuid)
                        .nickname("sangyun")
                        .gender("MALE")
                        .birth(LocalDate.of(1999,1,16))
                        .description("hi")
                        .profileUrl("https://example.com/profile.png")
                        .build());

        when(sessionManager.getUserIdOrThrow(any())).thenReturn(userId);
        when(csrfTokenManager.isValid(any(), any())).thenReturn(true);

        // then
        mockMvc.perform(get("/users/me")
                        .sessionAttr(USER_SESSION_KEY, userId))
                .andDo(document("users/me",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("publicUuid").description("암호화된 사용자 UUID"),
                                fieldWithPath("nickname").description("닉네임"),
                                fieldWithPath("gender").description("성별"),
                                fieldWithPath("birth").description("생일").optional(),
                                fieldWithPath("description").description("소개").optional(),
                                fieldWithPath("profileUrl").description("프로필 이미지 URL").optional()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicUuid").value(encryptedPublicUuid));
    }

    @Test
    @DisplayName("비로그인 사용자가 내 정보 조회 요청 시 401을 반환한다.")
    void 비로그인_사용자가_내_정보_조회_요청_시_401을_반환한다() throws Exception {
        when(sessionManager.getUserIdOrThrow(any()))
                .thenThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("인증정보가 없거나 만료되었습니다."))
                .andDo(document("users/me-fail-unauthorized",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )));

        verify(userService, never()).userInfo(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 내 정보 조회 요청 시 404를 반환한다.")
    void 존재하지_않는_사용자의_내_정보_조회_요청_시_404를_반환한다() throws Exception {
        Long userId = 1L;
        when(sessionManager.getUserIdOrThrow(any())).thenReturn(userId);
        when(userService.userInfo(userId))
                .thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/users/me")
                        .sessionAttr(USER_SESSION_KEY, userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 사용자 입니다."))
                .andDo(document("users/me-fail-user-not-found",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("message").description("오류 메시지")
                        )));
    }
}
