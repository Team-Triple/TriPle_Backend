package org.triple.backend.auth.unit.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.auth.session.PublicUuidCodec;
import org.triple.backend.auth.session.UuidCrypto;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.group.exception.GroupErrorCode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PublicUuidCodecTest {

    @Test
    @DisplayName("plain publicUuid를 encrypt하면 암호화된 문자열을 반환한다.")
    void plain_publicUuid를_encrypt하면_암호화된_문자열을_반환한다() {
        UuidCrypto uuidCrypto = mock(UuidCrypto.class);
        PublicUuidCodec codec = new PublicUuidCodec(uuidCrypto);
        UUID publicUuid = UUID.randomUUID();
        given(uuidCrypto.encrypt(publicUuid)).willReturn("encrypted-user-id");

        String encrypted = codec.encrypt(publicUuid.toString());

        assertThat(encrypted).isEqualTo("encrypted-user-id");
    }

    @Test
    @DisplayName("복호화 실패 시 전달받은 ErrorCode로 BusinessException을 던진다.")
    void 복호화_실패_시_전달받은_ErrorCode로_BusinessException을_던진다() {
        UuidCrypto uuidCrypto = mock(UuidCrypto.class);
        PublicUuidCodec codec = new PublicUuidCodec(uuidCrypto);
        given(uuidCrypto.decryptToUuid("invalid-token")).willReturn(null);

        assertThatThrownBy(() -> codec.decryptOrThrow("invalid-token", GroupErrorCode.NOT_GROUP_MEMBER))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GroupErrorCode.NOT_GROUP_MEMBER.getMessage());
    }
}
