package org.triple.backend.group.Integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.triple.backend.common.DbCleaner;
import org.triple.backend.common.annotation.IntegrationTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class GroupIntegrationTest {

    private static final String USER_SESSION_KEY = "USER_ID";

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
        String csrfToken = "csrf-token";
        var result = mockMvc.perform(post("/groups")
                        .sessionAttr(USER_SESSION_KEY, owner.getId())
                        .sessionAttr(CsrfTokenManager.CSRF_TOKEN_KEY, csrfToken)
                        .header(CsrfTokenManager.CSRF_HEADER, csrfToken)
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
}
