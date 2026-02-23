package org.triple.backend.user.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.triple.backend.common.annotation.IntegrationTest;
import org.triple.backend.user.entity.Gender;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.triple.backend.global.constants.AuthConstants.USER_SESSION_KEY;

@IntegrationTest
public class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Test
    @DisplayName("로그인 세션이 있으면 내 정보를 반환합니다.")
    void 로그인_세션이_있으면_내_정보를_반환합니다() throws Exception {
        // given
        User saved = userJpaRepository.save(User.builder()
                .nickname("상윤")
                .gender(Gender.MALE)
                .birth(LocalDate.of(1999, 1, 1))
                .description("소개글")
                .profileUrl("https://example.com/profile.png")
                .providerId("kakao-1234")
                .build());

        // when & then
        mockMvc.perform(get("/users/me")
                        .sessionAttr(USER_SESSION_KEY, saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("상윤"))
                .andExpect(jsonPath("$.gender").value(Gender.MALE.toString()))
                .andExpect(jsonPath("$.birth").value("1999-01-01"))
                .andExpect(jsonPath("$.description").value("소개글"))
                .andExpect(jsonPath("$.profileUrl").value("https://example.com/profile.png"));
    }

    @Test
    @DisplayName("세션이 없으면 401을 반환합니다.")
    void 세션이_없으면_401을_반환합니다() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("세션은 있는데 USER_ID가 없으면 401을 반환합니다.")
    void 세션은_있는데_USER_ID가_없으면_401을_반환합니다() throws Exception {
        mockMvc.perform(get("/users/me").sessionAttr("ANYTHING", 1L))
                .andExpect(status().isUnauthorized());
    }

}
