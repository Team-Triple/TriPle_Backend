package org.triple.backend.user.unit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.transfer.exception.TransferErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;
import org.triple.backend.user.service.UserFinder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class UserFinderTest {

    @Test
    @DisplayName("publicUuid가 null이면 예외가 발생한다")
    void publicUuid가_null이면_예외가_발생한다() {
        UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
        UserFinder userFinder = new UserFinder(userJpaRepository);

        assertThatThrownBy(() -> userFinder.findIdByPublicUuidOrThrow(null, TransferErrorCode.RECIPIENT_USER_NOT_FOUND))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TransferErrorCode.RECIPIENT_USER_NOT_FOUND.getMessage());

        then(userJpaRepository).should(never()).findIdByPublicUuid(any(UUID.class));
    }

    @Test
    @DisplayName("publicUuid가 공백이면 예외가 발생한다")
    void publicUuid가_공백이면_예외가_발생한다() {
        UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
        UserFinder userFinder = new UserFinder(userJpaRepository);

        assertThatThrownBy(() -> userFinder.findIdByPublicUuidOrThrow("   ", TransferErrorCode.RECIPIENT_USER_NOT_FOUND))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TransferErrorCode.RECIPIENT_USER_NOT_FOUND.getMessage());

        then(userJpaRepository).should(never()).findIdByPublicUuid(any(UUID.class));
    }

    @Test
    @DisplayName("publicUuid 포맷이 잘못되면 예외가 발생한다")
    void publicUuid_포맷이_잘못되면_예외가_발생한다() {
        UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
        UserFinder userFinder = new UserFinder(userJpaRepository);

        assertThatThrownBy(() -> userFinder.findIdByPublicUuidOrThrow("invalid-uuid", TransferErrorCode.RECIPIENT_USER_NOT_FOUND))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TransferErrorCode.RECIPIENT_USER_NOT_FOUND.getMessage());

        then(userJpaRepository).should(never()).findIdByPublicUuid(any(UUID.class));
    }

    @Test
    @DisplayName("존재하지 않는 publicUuid면 예외가 발생한다")
    void 존재하지_않는_publicUuid면_예외가_발생한다() {
        UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
        UserFinder userFinder = new UserFinder(userJpaRepository);
        UUID publicUuid = UUID.randomUUID();
        given(userJpaRepository.findIdByPublicUuid(publicUuid)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userFinder.findIdByPublicUuidOrThrow(publicUuid.toString(), TransferErrorCode.RECIPIENT_USER_NOT_FOUND))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TransferErrorCode.RECIPIENT_USER_NOT_FOUND.getMessage());

        then(userJpaRepository).should().findIdByPublicUuid(publicUuid);
    }

    @Test
    @DisplayName("유효한 publicUuid면 내부 userId를 반환한다")
    void 유효한_publicUuid면_내부_userId를_반환한다() {
        UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
        UserFinder userFinder = new UserFinder(userJpaRepository);
        UUID publicUuid = UUID.randomUUID();
        given(userJpaRepository.findIdByPublicUuid(publicUuid)).willReturn(Optional.of(1L));

        Long userId = userFinder.findIdByPublicUuidOrThrow(publicUuid.toString(), TransferErrorCode.RECIPIENT_USER_NOT_FOUND);

        assertThat(userId).isEqualTo(1L);
        then(userJpaRepository).should().findIdByPublicUuid(publicUuid);
    }
}
