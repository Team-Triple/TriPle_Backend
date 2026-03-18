package org.triple.backend.user.unit.service;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.triple.backend.auth.session.UuidCrypto;
import org.triple.backend.common.annotation.ServiceTest;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.user.dto.request.UpdateUserInfoReq;
import org.triple.backend.user.dto.response.UserInfoResponseDto;
import org.triple.backend.user.entity.Gender;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;
import org.triple.backend.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({UserService.class, UuidCrypto.class})
@ServiceTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private UuidCrypto uuidCrypto;

    @Test
    @DisplayName("사용자 정보를 조회한다.")
    void userInfoReturnsUserInfo() {
        User user = User.builder()
                .nickname("상윤")
                .gender(Gender.MALE)
                .birth(LocalDate.of(1999, 1, 1))
                .description("소개글")
                .profileUrl("https://example.com/profile.png")
                .providerId("kakao-1234")
                .build();

        User saved = userJpaRepository.save(user);

        UserInfoResponseDto response = userService.userInfo(saved.getId());

        assertThat(uuidCrypto.decryptToUuid(response.publicUuid())).isEqualTo(saved.getPublicUuid());
        assertThat(response.nickname()).isEqualTo("상윤");
        assertThat(response.gender()).isEqualTo(Gender.MALE.toString());
        assertThat(response.birth()).isEqualTo(LocalDate.of(1999, 1, 1));
        assertThat(response.description()).isEqualTo("소개글");
        assertThat(response.profileUrl()).isEqualTo("https://example.com/profile.png");
    }

    @Test
    @DisplayName("존재하지 않는 사용자를 조회하면 예외가 발생한다.")
    void userInfoThrowsWhenUserMissing() {
        Long notExistsUserId = 9999L;

        assertThatThrownBy(() -> userService.userInfo(notExistsUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException exception = (BusinessException) e;
                    assertThat(exception.getMessage()).isEqualTo(UserErrorCode.USER_NOT_FOUND.getMessage());
                });
    }

    @Test
    @DisplayName("사용자 정보를 수정한다.")
    void updateUserInfoUpdatesUser() {
        User user = User.builder()
                .nickname("기존닉네임")
                .gender(Gender.MALE)
                .birth(LocalDate.of(1999, 1, 1))
                .description("기존소개")
                .profileUrl("https://example.com/original.png")
                .providerId("kakao-1234")
                .build();

        User saved = userJpaRepository.save(user);

        UpdateUserInfoReq request = new UpdateUserInfoReq(
                "새닉네임",
                Gender.FEMALE,
                LocalDate.of(2000, 2, 2),
                "새소개",
                "https://example.com/new.png"
        );

        userService.updateUserInfo(saved.getId(), request);

        User updated = userJpaRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getNickname()).isEqualTo("새닉네임");
        assertThat(updated.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(updated.getBirth()).isEqualTo(LocalDate.of(2000, 2, 2));
        assertThat(updated.getDescription()).isEqualTo("새소개");
        assertThat(updated.getProfileUrl()).isEqualTo("https://example.com/new.png");
    }

    @Test
    @DisplayName("존재하지 않는 사용자를 수정하면 예외가 발생한다.")
    void updateUserInfoThrowsWhenUserMissing() {
        UpdateUserInfoReq request = new UpdateUserInfoReq(
                "새닉네임",
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> userService.updateUserInfo(9999L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException exception = (BusinessException) e;
                    assertThat(exception.getMessage()).isEqualTo(UserErrorCode.USER_NOT_FOUND.getMessage());
                });
    }
}
