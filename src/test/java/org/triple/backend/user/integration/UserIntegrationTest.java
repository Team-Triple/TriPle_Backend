package org.triple.backend.user.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.triple.backend.auth.crypto.UuidCrypto;
import org.triple.backend.auth.jwt.JwtManager;
import org.triple.backend.common.annotation.IntegrationTest;
import org.triple.backend.user.entity.Gender;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private UuidCrypto uuidCrypto;

    @Autowired
    private JwtManager jwtManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("login header exists then users me returns profile")
    void loginHeaderReturnsMyInfo() throws Exception {
        User saved = userJpaRepository.save(User.builder()
                .nickname("상윤")
                .gender(Gender.MALE)
                .birth(LocalDate.of(1999, 1, 1))
                .description("소개글")
                .profileUrl("https://example.com/profile.png")
                .providerId("kakao-1234")
                .build());

        String content = mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + jwtManager.createAccessToken(saved.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("상윤"))
                .andExpect(jsonPath("$.gender").value(Gender.MALE.toString()))
                .andExpect(jsonPath("$.birth").value("1999-01-01"))
                .andExpect(jsonPath("$.description").value("소개글"))
                .andExpect(jsonPath("$.profileUrl").value("https://example.com/profile.png"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String encryptedResponseUuid = objectMapper.readTree(content).get("publicUuid").asText();
        org.assertj.core.api.Assertions.assertThat(uuidCrypto.decryptToUuid(encryptedResponseUuid))
                .isEqualTo(saved.getPublicUuid());
    }

    @Test
    @DisplayName("missing header returns unauthorized")
    void noSessionReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("irrelevant attributes without auth returns unauthorized")
    void noUserIdReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me").sessionAttr("ANYTHING", 1L))
                .andExpect(status().isUnauthorized());
    }
}
