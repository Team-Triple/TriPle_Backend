package org.triple.backend.global.unit.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.global.log.MaskUtil;

import static org.assertj.core.api.Assertions.assertThat;

class MaskUtilTest {

    @Test
    @DisplayName("maskId는 null과 짧은 아이디를 마스킹한다")
    void 아이디_널_및_짧은값_마스킹() {
        assertThat(MaskUtil.maskId(null)).isEqualTo("null");
        assertThat(MaskUtil.maskId(1L)).isEqualTo("**");
        assertThat(MaskUtil.maskId(12L)).isEqualTo("**");
    }

    @Test
    @DisplayName("maskId는 긴 아이디에서 앞뒤 한 글자만 노출한다")
    void 아이디_긴값_앞뒤노출() {
        assertThat(MaskUtil.maskId(12345L)).isEqualTo("1***5");
    }

    @Test
    @DisplayName("maskString은 null과 blank를 처리한다")
    void 문자열_널_및_공백_처리() {
        assertThat(MaskUtil.maskString(null)).isEqualTo("null");
        assertThat(MaskUtil.maskString(" ")).isEqualTo("(blank)");
    }

    @Test
    @DisplayName("maskString은 짧은 문자열을 마스킹한다")
    void 문자열_짧은값_마스킹() {
        assertThat(MaskUtil.maskString("a")).isEqualTo("*");
        assertThat(MaskUtil.maskString("ab")).isEqualTo("**");
        assertThat(MaskUtil.maskString("abcd")).isEqualTo("a**d");
    }

    @Test
    @DisplayName("maskString은 긴 문자열에서 앞뒤 4글자만 노출한다")
    void 문자열_긴값_앞뒤4글자_노출() {
        assertThat(MaskUtil.maskString("abcdefghijkl"))
                .isEqualTo("abcd****ijkl");
    }
}
