package org.triple.backend.file.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.file.config.property.S3BucketProperties;
import org.triple.backend.file.config.property.S3PrefixProperties;
import org.triple.backend.file.config.property.S3PresignProperties;
import org.triple.backend.file.config.property.S3UploadPolicyProperties;
import software.amazon.awssdk.regions.Region;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BucketKeyPublisherTest {

    private final BucketKeyPublisher bucketKeyPublisher = new BucketKeyPublisher(
            new S3BucketProperties(
                    Region.AP_NORTHEAST_2,
                    "test-bucket",
                    new S3PresignProperties(180),
                    new S3PrefixProperties(
                            "uploads/pending/",
                            "uploads/uploaded/",
                            "https://triple-dev-s3.s3.ap-northeast-2.amazonaws.com/"
                    ),
                    new S3UploadPolicyProperties(List.of("jpg", "png"), List.of("image/jpeg", "image/png"))
            )
    );

    @Test
    @DisplayName("파일명과 사용자 ID로 pending key를 생성한다.")
    void 파일명과_사용자_ID로_pending_key를_생성한다() {
        // given
        String fileName = "test.jpg";
        Long userId = 1L;

        // when
        String key = bucketKeyPublisher.publishPendingKey(fileName, userId);

        // then
        assertThat(key).matches("^uploads/pending/1/[a-f0-9]{32}\\.jpg$");
    }

    @Test
    @DisplayName("파일명이 null 또는 공백이면 pending key 생성 시 예외를 던진다.")
    void 파일명이_null_또는_공백이면_pending_key_생성_시_예외를_던진다() {
        assertThatThrownBy(() -> bucketKeyPublisher.publishPendingKey(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bucketKeyPublisher.publishPendingKey(" ", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("확장자가 없는 파일명으로 pending key 생성 시 예외를 던진다.")
    void 확장자가_없는_파일명으로_pending_key_생성_시_예외를_던진다() {
        assertThatThrownBy(() -> bucketKeyPublisher.publishPendingKey("test", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bucketKeyPublisher.publishPendingKey(".jpg", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bucketKeyPublisher.publishPendingKey("test.", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("pending key를 uploaded prefix로 변환한다.")
    void pending_key를_uploaded_prefix로_변환한다() {
        // given
        String pendingKey = "uploads/pending/1/abc123.jpg";

        // when
        String uploadedKey = bucketKeyPublisher.publishUploadedKey(pendingKey);

        // then
        assertThat(uploadedKey).isEqualTo("uploads/uploaded/1/abc123.jpg");
    }

    @Test
    @DisplayName("pending key가 null 또는 공백이면 uploaded key 변환 시 예외를 던진다.")
    void pending_key가_null_또는_공백이면_uploaded_key_변환_시_예외를_던진다() {
        assertThatThrownBy(() -> bucketKeyPublisher.publishUploadedKey(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bucketKeyPublisher.publishUploadedKey(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("pending prefix가 아니면 uploaded key 변환 시 예외를 던진다.")
    void pending_prefix가_아니면_uploaded_key_변환_시_예외를_던진다() {
        assertThatThrownBy(() -> bucketKeyPublisher.publishUploadedKey("uploads/other/1/test.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("pending key 형식이 잘못되면 uploaded key 변환 시 예외를 던진다.")
    void pending_key_형식이_잘못되면_uploaded_key_변환_시_예외를_던진다() {
        assertThatThrownBy(() -> bucketKeyPublisher.publishUploadedKey("uploads/pending/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bucketKeyPublisher.publishUploadedKey("uploads/pending/test.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
