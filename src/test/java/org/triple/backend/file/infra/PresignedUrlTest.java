package org.triple.backend.file.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URL;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

class PresignedUrlTest {

    @Test
    @DisplayName("유효한 값으로 PresignedUrl을 생성하면 key/url/만료시간을 반환한다.")
    void 유효한_값으로_PresignedUrl을_생성하면_key_url_만료시간을_반환한다() throws Exception {
        // given
        PresignedPutObjectRequest request = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        given(request.url()).willReturn(new URL("https://example.com/upload"));
        given(request.expiration()).willReturn(Instant.parse("2030-01-01T00:00:00Z"));

        // when
        PresignedUrl presignedUrl = new PresignedUrl("uploads/pending/1/a.jpg", request);

        // then
        assertThat(presignedUrl.key()).isEqualTo("uploads/pending/1/a.jpg");
        assertThat(presignedUrl.presignedUrl()).isEqualTo("https://example.com/upload");
        assertThat(presignedUrl.expiresAt()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("key가 null 또는 공백이면 생성 시 예외를 던진다.")
    void key가_null_또는_공백이면_생성_시_예외를_던진다() {
        PresignedPutObjectRequest request = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);

        assertThatThrownBy(() -> new PresignedUrl(null, request))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PresignedUrl(" ", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PresignedPutObjectRequest가 null이면 생성 시 예외를 던진다.")
    void PresignedPutObjectRequest가_null이면_생성_시_예외를_던진다() {
        assertThatThrownBy(() -> new PresignedUrl("uploads/pending/1/a.jpg", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("요청 객체에 URL이 없으면 presignedUrl 조회 시 예외를 던진다.")
    void 요청_객체에_URL이_없으면_presignedUrl_조회_시_예외를_던진다() {
        // given
        PresignedPutObjectRequest request = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        PresignedUrl presignedUrl = new PresignedUrl("uploads/pending/1/a.jpg", request);
        given(request.url()).willReturn(null);

        // when & then
        assertThatThrownBy(presignedUrl::presignedUrl)
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("요청 객체에 만료시간이 없으면 expiresAt 조회 시 예외를 던진다.")
    void 요청_객체에_만료시간이_없으면_expiresAt_조회_시_예외를_던진다() {
        // given
        PresignedPutObjectRequest request = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        PresignedUrl presignedUrl = new PresignedUrl("uploads/pending/1/a.jpg", request);
        given(request.expiration()).willReturn(null);

        // when & then
        assertThatThrownBy(presignedUrl::expiresAt)
                .isInstanceOf(NullPointerException.class);
    }
}
