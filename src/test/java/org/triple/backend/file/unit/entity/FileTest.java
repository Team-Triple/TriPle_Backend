package org.triple.backend.file.unit.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.file.entity.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileTest {
    @Test
    @DisplayName("유효한 ownerId와 key로 파일 엔티티를 생성한다.")
    void 파일_엔티티를_생성() {
        // given
        Long userId = 1L;
        String key = "uploads/uploaded/1/test.jpg";

        // when
        File file = File.of(userId, key);

        // then
        assertThat(file).extracting("ownerId", "key").containsExactly(userId, key);
    }

    @Test
    @DisplayName("ownerId가 null 또는 0 이하이면 예외를 던진다.")
    void ownerId_null_0_예외() {
        assertThatThrownBy(() -> File.of(null, "uploads/uploaded/1/test.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> File.of(0L, "uploads/uploaded/1/test.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> File.of(-1L, "uploads/uploaded/1/test.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("key가 null 또는 공백이면 예외를 던진다.")
    void key가_null_공백_예외() {
        assertThatThrownBy(() -> File.of(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> File.of(1L, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> File.of(1L, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("key 길이가 255자를 초과하면 예외를 던진다.")
    void key_길이가_예외() {
        // given
        String tooLongKey = "a".repeat(256);

        // when & then
        assertThatThrownBy(() -> File.of(1L, tooLongKey))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
