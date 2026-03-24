package org.triple.backend.group.unit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.triple.backend.auth.crypto.PublicUuidCodec;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.group.controller.GroupController;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.request.GroupUpdateRequestDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.dto.response.GroupDetailResponseDto;
import org.triple.backend.group.dto.response.GroupMenuResponseDto;
import org.triple.backend.group.dto.response.GroupUpdateResponseDto;
import org.triple.backend.group.dto.response.GroupUsersResponseDto;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.exception.GroupErrorCode;
import org.triple.backend.group.service.GroupService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
class GroupControllerTest extends ControllerTest {

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private PublicUuidCodec publicUuidCodec;

    @Test
    @DisplayName("create group returns group id")
    void createGroup() throws Exception {
        given(groupService.create(any(CreateGroupRequestDto.class), eq(1L)))
                .willReturn(new CreateGroupResponseDto(1L));

        String body = """
                {
                  "name": "trip",
                  "description": "desc",
                  "memberLimit": 10,
                  "groupKind": "PUBLIC",
                  "thumbNailUrl": "https://example.com/thumb.png"
                }
                """;

        mockMvc.perform(post("/groups")
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(1L));
    }

    @Test
    @DisplayName("create group without login returns unauthorized")
    void createGroupUnauthorized() throws Exception {
        String body = """
                {
                  "name": "trip",
                  "description": "desc",
                  "memberLimit": 10,
                  "groupKind": "PUBLIC",
                  "thumbNailUrl": "https://example.com/thumb.png"
                }
                """;

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(groupService, never()).create(any(CreateGroupRequestDto.class), any(Long.class));
    }

    @Test
    @DisplayName("browse public groups returns cursor response")
    void browsePublicGroups() throws Exception {
        GroupCursorResponseDto response = new GroupCursorResponseDto(
                List.of(new GroupCursorResponseDto.GroupSummaryDto(1L, "trip", "desc", 1, 10, "thumb")),
                null,
                false
        );
        given(groupService.search(eq("trip"), eq(null), eq(10))).willReturn(response);

        mockMvc.perform(get("/groups")
                        .param("keyword", "trip")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].groupId").value(1L));
    }

    @Test
    @DisplayName("delete group with login returns ok")
    void deleteGroup() throws Exception {
        mockMvc.perform(delete("/groups/{groupId}", 1L)
                        .with(loginJwt()))
                .andExpect(status().isOk());

        verify(groupService, times(1)).delete(1L, 1L);
    }

    @Test
    @DisplayName("update group returns updated response")
    void updateGroup() throws Exception {
        GroupUpdateResponseDto response = new GroupUpdateResponseDto(
                1L,
                GroupKind.PUBLIC,
                "name",
                "desc",
                "thumb",
                10,
                1
        );
        given(groupService.update(any(GroupUpdateRequestDto.class), eq(1L), eq(1L))).willReturn(response);

        String body = """
                {
                  "name": "name",
                  "description": "desc",
                  "memberLimit": 10,
                  "groupKind": "PUBLIC",
                  "thumbNailUrl": "thumb"
                }
                """;

        mockMvc.perform(patch("/groups/{groupId}", 1L)
                        .with(loginJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(1L));
    }

    @Test
    @DisplayName("group detail returns response")
    void groupDetail() throws Exception {
        GroupDetailResponseDto response = new GroupDetailResponseDto(
                List.of(),
                "group",
                "desc",
                GroupKind.PUBLIC,
                "thumb",
                1,
                10,
                Role.GUEST,
                List.of(),
                0,
                List.of(),
                List.of()
        );
        given(groupService.detail(1L, 1L)).willReturn(response);

        mockMvc.perform(get("/groups/{groupId}", 1L).with(loginJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("group"));
    }

    @Test
    @DisplayName("transfer owner decrypts user id and calls service")
    void transferOwner() throws Exception {
        String publicUuid = "00000000-0000-0000-0000-000000000002";
        given(publicUuidCodec.decryptOrThrow(eq("enc-user"), eq(GroupErrorCode.NOT_GROUP_MEMBER))).willReturn(publicUuid);

        mockMvc.perform(patch("/groups/{groupId}/owner/{targetUserId}", 1L, "enc-user")
                        .with(loginJwt()))
                .andExpect(status().isOk());

        verify(groupService, times(1)).ownerTransfer(1L, publicUuid, 1L);
    }

    @Test
    @DisplayName("kick member decrypts user id and calls service")
    void kickMember() throws Exception {
        String publicUuid = "00000000-0000-0000-0000-000000000002";
        given(publicUuidCodec.decryptOrThrow(eq("enc-user"), eq(GroupErrorCode.NOT_GROUP_MEMBER))).willReturn(publicUuid);

        mockMvc.perform(delete("/groups/{groupId}/users/{targetUserId}", 1L, "enc-user")
                        .with(loginJwt()))
                .andExpect(status().isOk());

        verify(groupService, times(1)).kick(1L, 1L, publicUuid);
    }

    @Test
    @DisplayName("leave group calls service")
    void leaveGroup() throws Exception {
        mockMvc.perform(delete("/groups/{groupId}/users/me", 1L).with(loginJwt()))
                .andExpect(status().isOk());

        verify(groupService, times(1)).leave(1L, 1L);
    }

    @Test
    @DisplayName("my groups returns cursor response")
    void myGroups() throws Exception {
        GroupCursorResponseDto response = new GroupCursorResponseDto(List.of(), null, false);
        given(groupService.myGroups(null, 10, 1L)).willReturn(response);

        mockMvc.perform(get("/groups/me")
                        .with(loginJwt())
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("menu returns menu response")
    void menu() throws Exception {
        GroupMenuResponseDto response = new GroupMenuResponseDto(
                "group",
                "desc",
                1,
                10,
                "thumb",
                Role.OWNER
        );
        given(groupService.menu(1L, 1L)).willReturn(response);

        mockMvc.perform(get("/groups/{groupId}/menu", 1L).with(loginJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("group"));
    }

    @Test
    @DisplayName("group users encrypts ids")
    void groupUsers() throws Exception {
        GroupUsersResponseDto response = new GroupUsersResponseDto(
                List.of(new GroupUsersResponseDto.UserDto("1", "nick", "desc", "profile", true))
        );
        given(groupService.groupUsers(1L)).willReturn(response);
        given(publicUuidCodec.encrypt("1")).willReturn("enc-1");

        mockMvc.perform(get("/groups/{groupId}/users", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].id").value("enc-1"));
    }

    private RequestPostProcessor loginJwt() {
        return request -> {
            request.addHeader("Authorization", "Bearer test-token");
            return request;
        };
    }
}
