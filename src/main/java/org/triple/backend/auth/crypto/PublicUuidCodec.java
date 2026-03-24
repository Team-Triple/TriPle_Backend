package org.triple.backend.auth.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.global.error.ErrorCode;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PublicUuidCodec {

    private final UuidCrypto uuidCrypto;

    public String encrypt(final String plainPublicUuid) {
        return uuidCrypto.encrypt(UUID.fromString(plainPublicUuid));
    }

    public String decryptOrThrow(final String encryptedPublicUuid, final ErrorCode errorCode) {
        UUID publicUuid = uuidCrypto.decryptToUuid(encryptedPublicUuid);
        if (publicUuid == null) {
            throw new BusinessException(errorCode);
        }
        return publicUuid.toString();
    }
}
