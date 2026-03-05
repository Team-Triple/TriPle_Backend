package org.triple.backend.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.global.error.ErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserFinder {

    private final UserJpaRepository userJpaRepository;

    public Long findIdByPublicUuidOrThrow(final String publicUuid, final ErrorCode errorCode) {
        if (publicUuid == null || publicUuid.isBlank()) {
            throw new BusinessException(errorCode);
        }

        UUID parsedPublicUuid;
        try {
            parsedPublicUuid = UUID.fromString(publicUuid);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(errorCode);
        }

        return userJpaRepository.findIdByPublicUuid(parsedPublicUuid)
                .orElseThrow(() -> new BusinessException(errorCode));
    }
}
