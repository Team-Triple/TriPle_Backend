package org.triple.backend.user.unit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.user.dto.response.UserInfoResponseDto;
import org.triple.backend.user.entity.Gender;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;
import org.triple.backend.user.service.UserService;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(UserService.class)
@ServiceTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Test
    @DisplayName("사용자 정보를 조회한다.")
    void 사용자_정보_조회한다() {
        // given
        User user = User.builder()
                .nickname("상윤")
                .gender(Gender.MALE)
                .birth(LocalDate.of(1999, 1, 1))
                .description("소개글")
                .profileUrl("https://example.com/profile.png")
                .providerId("kakao-1234")
                .build();

        User saved = userJpaRepository.save(user);

        // when
        UserInfoResponseDto response = userService.userInfo(saved.getId());

        // then
        assertThat(response.nickname()).isEqualTo("상윤");
        assertThat(response.gender()).isEqualTo(Gender.MALE.toString());
        assertThat(response.birth()).isEqualTo(LocalDate.of(1999, 1, 1));
        assertThat(response.description()).isEqualTo("소개글");
        assertThat(response.profileUrl()).isEqualTo("https://example.com/profile.png");
    }

    @Test
    @DisplayName("존재하지 않는 사용자를 조회하면 예외가 발생한다.")
    void 존재하지_않는_사용자를_조회하면_예외가_발생한다() {
        // given
        Long notExistsUserId = 9999L;

        // when & then
        assertThatThrownBy(() -> userService.userInfo(notExistsUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException exception = (BusinessException) e;
                    assertThat(exception.getMessage()).isEqualTo(UserErrorCode.USER_NOT_FOUND.getMessage());
                });
    }

}