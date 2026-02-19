package org.triple.backend.group.Integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.triple.backend.common.DbCleaner;
import org.triple.backend.common.annotation.IntegrationTest;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.entity.userGroup.Role;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.repository.UserGroupJpaRepository;
import org.triple.backend.auth.session.CsrfTokenManager;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.group.fixture.GroupFixtures.privateGroup;
import static org.triple.backend.group.fixture.GroupFixtures.publicGroup;

@IntegrationTest
public class GroupIntegrationTest {

    private static final String USER_SESSION_KEY = "USER_ID";
    private static final String CSRF_TOKEN = "csrf-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private UserGroupJpaRepository userGroupJpaRepository;

    @Autowired
    private DbCleaner dbCleaner;

    @BeforeEach
    void setUp() {
        dbCleaner.clean();
    }

    @Test
    @DisplayName("로그인 세션이 있으면 그룹 생성이 되고, 생성자는 OWNER로 UserGroup이 저장된다")
    void 로그인_세션이_있으면_그룹_생성이_되고_생성자는_OWNER로_UserGroup이_저장된다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-1")
                        .nickname("상윤")
                        .email("test@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        String body = """
                {
                  "name": "여행모임",
                  "description": "3월 일본 여행",
                  "memberLimit": 10,
                  "groupKind": "PUBLIC",
                  "thumbNailUrl": "https://example.com/thumb.png"
                }
                """;

        // when
        var result = mockMvc.perform(post("/groups")
                        .sessionAttr(USER_SESSION_KEY, owner.getId())
                        .sessionAttr(CsrfTokenManager.CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").exists())
                .andReturn();

        // then
        String response = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        long groupId = root.get("groupId").asLong();

        Group savedGroup = groupJpaRepository.findById(groupId).orElseThrow();
        assertThat(savedGroup.getName()).isEqualTo("여행모임");
        assertThat(savedGroup.getMemberLimit()).isEqualTo(10);
        assertThat(savedGroup.getGroupKind()).isEqualTo(GroupKind.PUBLIC);

        // then
        List<UserGroup> userGroups = userGroupJpaRepository.findAll();
        assertThat(userGroups).hasSize(1);
        UserGroup userGroup = userGroups.get(0);

        assertThat(userGroup.getUser().getId()).isEqualTo(owner.getId());
        assertThat(userGroup.getGroup().getId()).isEqualTo(savedGroup.getId());
        assertThat(userGroup.getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    @DisplayName("공개 그룹 목록 첫 페이지를 조회하면 PUBLIC만 size개 조회된다")
    void 공개_그룹_목록_첫_페이지_조회하면_PUBLIC만_size개_조회된다() throws Exception {
        // given
        for (int i = 1; i <= 12; i++) {
            groupJpaRepository.save(publicGroup("public-" + i));
        }

        for (int i = 1; i <= 3; i++) {
            groupJpaRepository.save(privateGroup("private-" + i));
        }

        // when & then
        mockMvc.perform(get("/groups")
                        .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isNumber())
                .andExpect(jsonPath("$.items[*].name", everyItem(startsWith("public-"))));
    }

    @Test
    @DisplayName("공개 그룹 목록 다음 페이지를 조회하면 cursor 기준으로 조회된다")
    void 공개_그룹_목록_다음_페이지_조회하면_cursor_기준으로_조회된다() throws Exception {
        // given
        for (int i = 1; i <= 15; i++) groupJpaRepository.save(publicGroup("public-" + i));

        String firstJson = mockMvc.perform(get("/groups").param("size", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        GroupCursorResponseDto first = objectMapper.readValue(firstJson, GroupCursorResponseDto.class);
        Long nextCursor = first.nextCursor();

        // when & then
        mockMvc.perform(get("/groups")
                        .param("cursor", String.valueOf(nextCursor))
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(result -> {
                    String json = result.getResponse().getContentAsString();
                    GroupCursorResponseDto dto = objectMapper.readValue(json, GroupCursorResponseDto.class);
                    assertThat(dto.items())
                            .allSatisfy(item -> assertThat(item.groupId()).isLessThan(nextCursor));
                });
    }

    @Test
    @DisplayName("로그인 세션이 있으면 그룹 삭제가 되고, 연관된 UserGroup도 함께 삭제된다")
    void 로그인_세션이_있으면_그룹_삭제가_되고_연관된_UserGroup도_함께_삭제된다() throws Exception {
        // given
        User owner = userJpaRepository.save(
                User.builder()
                        .providerId("kakao-owner")
                        .nickname("상윤")
                        .email("owner@test.com")
                        .profileUrl("http://img")
                        .build()
        );

        Group group = Group.create(GroupKind.PUBLIC, "여행모임", "설명", "thumb", 10);
        group.addMember(owner, Role.OWNER);

        Group savedGroup = groupJpaRepository.saveAndFlush(group);

        assertThat(groupJpaRepository.findById(savedGroup.getId())).isPresent();
        assertThat(userGroupJpaRepository.findAll()).hasSize(1);

        // when
        mockMvc.perform(delete("/groups/{groupId}", savedGroup.getId())
                        .sessionAttr(USER_SESSION_KEY, owner.getId())
                        .sessionAttr(CsrfTokenManager.CSRF_TOKEN_KEY, CSRF_TOKEN)
                        .header(CsrfTokenManager.CSRF_HEADER, CSRF_TOKEN))
                .andExpect(status().isOk());

        // then
        assertThat(groupJpaRepository.findById(savedGroup.getId())).isEmpty();
        assertThat(userGroupJpaRepository.findAll()).isEmpty();
    }
}