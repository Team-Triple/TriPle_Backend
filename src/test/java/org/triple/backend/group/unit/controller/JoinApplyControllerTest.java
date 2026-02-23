package org.triple.backend.group.unit.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.group.controller.JoinApplyController;
import org.triple.backend.group.service.JoinApplyService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(status().isUnauthorized());

        verify(joinApplyService, never()).joinApply(any(Long.class), any(Long.class));
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
