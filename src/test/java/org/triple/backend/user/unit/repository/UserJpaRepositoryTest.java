package org.triple.backend.user.unit.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.triple.backend.common.annotation.RepositoryTest;
import org.triple.backend.user.entity.Gender;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.repository.UserJpaRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class UserJpaRepositoryTest {

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Test
    @DisplayName("저장한 사용자를 ID로 조회시 존재하면 User 엔티티를 반환한다.")
    void 저장한_사용자를_ID로_조회시_존재하면_User_엔티티를_반환한다() {
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
        User found = userJpaRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getNickname()).isEqualTo(saved.getNickname());
        assertThat(found.getGender()).isEqualTo(saved.getGender());
        assertThat(found.getBirth()).isEqualTo(saved.getBirth());
        assertThat(found.getDescription()).isEqualTo(saved.getDescription());
        assertThat(found.getProfileUrl()).isEqualTo(saved.getProfileUrl());
        assertThat(found.getProviderId()).isEqualTo(saved.getProviderId());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 empty를 반환한다")
    void 존재하지_않는_사용자면_empty를_반환한다() {
        // when
        var result = userJpaRepository.findById(999999L);

        // then
        assertThat(result).isEmpty();
    }

}
