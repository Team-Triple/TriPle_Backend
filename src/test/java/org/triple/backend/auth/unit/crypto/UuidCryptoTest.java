package org.triple.backend.auth.unit.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.auth.crypto.UuidCrypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidCryptoTest {

    private final UuidCrypto uuidCrypto = new UuidCrypto("test-uuid-secret-value-at-least-32-chars");

    @Test
    @DisplayName("encrypt and decrypt round trip")
    void encryptDecryptRoundTrip() {
        UUID uuid = UUID.randomUUID();

        String encrypted = uuidCrypto.encrypt(uuid);
        UUID decrypted = uuidCrypto.decryptToUuid(encrypted);

        assertThat(decrypted).isEqualTo(uuid);
    }

    @Test
    @DisplayName("decrypt returns null for non string principal")
    void decryptReturnsNullForNonString() {
        assertThat(uuidCrypto.decryptToUuid(123L)).isNull();
    }

    @Test
    @DisplayName("decrypt returns null for blank token")
    void decryptReturnsNullForBlank() {
        assertThat(uuidCrypto.decryptToUuid(" ")).isNull();
    }

    @Test
    @DisplayName("decrypt returns null for too short payload")
    void decryptReturnsNullForShortPayload() {
        String shortToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("x".getBytes(StandardCharsets.UTF_8));

        assertThat(uuidCrypto.decryptToUuid(shortToken)).isNull();
    }

    @Test
    @DisplayName("decrypt returns null for malformed token")
    void decryptReturnsNullForMalformedToken() {
        assertThat(uuidCrypto.decryptToUuid("%%%not-base64%%%")).isNull();
    }
}
