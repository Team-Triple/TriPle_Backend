package org.triple.backend.user.unit.entity;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.user.dto.request.UpdateUserInfoReq;
import org.triple.backend.user.entity.Gender;
import org.triple.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    @DisplayName("patchUserInfo는 null이 아닌 값만 검증 후 반영한다")
    void patchUserInfoReflectsValidatedFieldsOnly() {
        User user = User.builder()
                .nickname("기존닉네임")
                .gender(Gender.MALE)
                .birth(LocalDate.of(1995, 5, 5))
                .description("기존 소개")
                .profileUrl("https://example.com/original.png")
                .build();

        UpdateUserInfoReq req = new UpdateUserInfoReq(
                " 새닉네임 ",
                Gender.FEMALE,
                LocalDate.of(2000, 1, 1),
                "  새 소개  ",
                " https://example.com/new.png "
        );

        user.patchUserInfo(req);

        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(user.getBirth()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(user.getDescription()).isEqualTo("새 소개");
        assertThat(user.getProfileUrl()).isEqualTo("https://example.com/new.png");
    }

    @Test
    @DisplayName("patchUserInfo는 공백 닉네임을 거부한다")
    void patchUserInfoRejectsBlankNickname() {
        User user = User.builder().build();

        assertThatThrownBy(() -> user.patchUserInfo(
                new UpdateUserInfoReq("   ", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("닉네임");
    }

    @Test
    @DisplayName("patchUserInfo는 미래 생년월일을 거부한다")
    void patchUserInfoRejectsFutureBirth() {
        User user = User.builder().build();

        assertThatThrownBy(() -> user.patchUserInfo(
                new UpdateUserInfoReq(null, null, LocalDate.now().plusDays(1), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("생년월일");
    }

    @Test
    @DisplayName("patchUserInfo는 공백 소개글을 거부한다")
    void patchUserInfoRejectsBlankDescription() {
        User user = User.builder().build();

        assertThatThrownBy(() -> user.patchUserInfo(
                new UpdateUserInfoReq(null, null, null, "   ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("소개글");
    }

    @Test
    @DisplayName("patchUserInfo는 공백 프로필 URL을 거부한다")
    void patchUserInfoRejectsBlankProfileUrl() {
        User user = User.builder().build();

        assertThatThrownBy(() -> user.patchUserInfo(
                new UpdateUserInfoReq(null, null, null, null, "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("프로필 URL");
    }
}
