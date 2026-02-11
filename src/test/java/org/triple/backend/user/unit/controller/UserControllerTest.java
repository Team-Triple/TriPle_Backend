package org.triple.backend.user.unit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.triple.backend.common.ControllerTest;
import org.triple.backend.user.controller.UserController;
import org.triple.backend.user.dto.response.UserInfoResponseDto;
import org.triple.backend.user.service.UserService;

import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
public class UserControllerTest extends ControllerTest{

    @MockitoBean
    protected UserService userService;

    @Test
    @DisplayName("사용자 정보 조회를 하면 사용자 정보와 상태코드 200을 반환한다.")
    void 사용자_정보_조회를_하면_사용자_정보와_상태코드_200을_반환한다() throws Exception {
        // given
        Long userId = 1L;

        // when
        when(userService.userInfo(userId))
                .thenReturn(UserInfoResponseDto.builder()
                        .userId(userId)
                        .nickname("sangyun")
                        .gender("MALE")
                        .birth(LocalDate.of(1999,1,16))
                        .description("hi")
                        .profileUrl("https://example.com/profile.png")
                        .build());

        // then
        mockMvc.perform(get("/users/me")
                        .sessionAttr("USER_ID", userId))
                .andDo(document("users/me",
                        responseFields(
                                fieldWithPath("userId").description("유저 ID"),
                                fieldWithPath("nickname").description("닉네임"),
                                fieldWithPath("gender").description("성별"),
                                fieldWithPath("birth").description("생일").optional(),
                                fieldWithPath("description").description("소개").optional(),
                                fieldWithPath("profileUrl").description("프로필 이미지 URL").optional()
                        )))
                .andExpect(status().isOk());
    }

}