package org.triple.backend.group.unit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.group.controller.JoinApplyController;
import org.triple.backend.group.dto.response.JoinApplyUserResponseDto;
import org.triple.backend.group.entity.joinApply.JoinApplyStatus;
import org.triple.backend.group.service.JoinApplyService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JoinApplyController.class)
class JoinApplyControllerTest extends ControllerTest {

    @MockitoBean
    private JoinApplyService joinApplyService;

    @Test
    @DisplayName("join apply with login returns ok")
    void joinApply() throws Exception {
        mockMvc.perform(post("/groups/{groupId}/join-applies", 1L)
                        .with(loginJwt()))
                .andExpect(status().isOk());

        verify(joinApplyService, times(1)).joinApply(1L, 1L);
    }

    @Test
    @DisplayName("join apply without login returns unauthorized")
    void joinApplyUnauthorized() throws Exception {
        mockMvc.perform(post("/groups/{groupId}/join-applies", 1L))
                .andExpect(status().isUnauthorized());

        verify(joinApplyService, never()).joinApply(any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("approve join apply with login returns ok")
    void approveJoinApply() throws Exception {
        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}", 1L, 2L)
                        .with(loginJwt()))
                .andExpect(status().isOk());

        verify(joinApplyService, times(1)).approve(1L, 1L, 2L);
    }

    @Test
    @DisplayName("reject join apply with login returns ok")
    void rejectJoinApply() throws Exception {
        mockMvc.perform(post("/groups/{groupId}/join-applies/{joinApplyId}/reject", 1L, 2L)
                        .with(loginJwt()))
                .andExpect(status().isOk());

        verify(joinApplyService, times(1)).reject(1L, 1L, 2L);
    }

    @Test
    @DisplayName("join apply users returns response")
    void joinApplyUsers() throws Exception {
        JoinApplyUserResponseDto response = new JoinApplyUserResponseDto(
                List.of(new JoinApplyUserResponseDto.UserDto(101L, "nick", "desc", "profile", JoinApplyStatus.PENDING)),
                null,
                false
        );
        given(joinApplyService.joinApplyUser(1L, 1L, JoinApplyStatus.PENDING, null, 10)).willReturn(response);

        mockMvc.perform(get("/groups/{groupId}/join-applies", 1L)
                        .with(loginJwt())
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].joinApplyId").value(101L))
                .andExpect(jsonPath("$.users[0].status").value("PENDING"))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(joinApplyService, times(1)).joinApplyUser(1L, 1L, JoinApplyStatus.PENDING, null, 10);
    }

    @Test
    @DisplayName("join apply users without login returns unauthorized")
    void joinApplyUsersUnauthorized() throws Exception {
        mockMvc.perform(get("/groups/{groupId}/join-applies", 1L)
                        .param("status", "PENDING"))
                .andExpect(status().isUnauthorized());

        verify(joinApplyService, never()).joinApplyUser(
                any(Long.class),
                any(Long.class),
                any(JoinApplyStatus.class),
                any(Long.class),
                anyInt()
        );
    }

    @Test
    @DisplayName("join apply users with invalid status returns bad request")
    void joinApplyUsersBadRequest() throws Exception {
        mockMvc.perform(get("/groups/{groupId}/join-applies", 1L)
                        .with(loginJwt())
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest());

        verify(joinApplyService, never()).joinApplyUser(
                any(Long.class),
                any(Long.class),
                any(JoinApplyStatus.class),
                any(Long.class),
                anyInt()
        );
    }

    private RequestPostProcessor loginJwt() {
        return request -> {
            request.addHeader("Authorization", "Bearer test-token");
            return request;
        };
    }
}
