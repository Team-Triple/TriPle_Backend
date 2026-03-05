package org.triple.backend.auth.unit.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.auth.session.UuidCrypto;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidCryptoTest {

    @Test
    @DisplayName("UUID를 암호화한 뒤 복호화하면 원본 UUID와 같다")
    void UUID_암호화_후_복호화하면_원본과_같다() {
        // given
        UuidCrypto uuidCrypto = new UuidCrypto("test-uuid-crypto-secret");
        UUID uuid = UUID.randomUUID();

        // when
        String encrypted = uuidCrypto.encrypt(uuid);
        UUID decrypted = uuidCrypto.decryptToUuid(encrypted);

        // then
        assertThat(encrypted).isNotBlank();
        assertThat(decrypted).isEqualTo(uuid);
    }

    @Test
    @DisplayName("잘못된 암호문을 복호화하면 null을 반환한다")
    void 잘못된_암호문_복호화시_null을_반환한다() {
        // given
        UuidCrypto uuidCrypto = new UuidCrypto("test-uuid-crypto-secret");

        // when
        UUID decrypted = uuidCrypto.decryptToUuid("invalid-cipher-text");

        // then
        assertThat(decrypted).isNull();
    }
}
