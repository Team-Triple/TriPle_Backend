package org.triple.backend.auth.unit.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.triple.backend.auth.session.UuidCrypto;
import org.triple.backend.auth.session.UserIdentityResolver;
import org.triple.backend.user.repository.UserJpaRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class UserIdentityResolverTest {

    @Test
    @DisplayName("principal이 null이면 null을 반환한다")
    void principal이_null이면_null을_반환한다() {
        // given
        UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
        UuidCrypto uuidCrypto = mock(UuidCrypto.class);
        UserIdentityResolver resolver = resolverWith(userJpaRepository, uuidCrypto);

        // when
        Long userId = resolver.resolve(null);

        // then
        assertThat(userId).isNull();
    }

    @Test
    @DisplayName("principal이 Long이면 null을 반환한다")
    void principal이_Long이면_null을_반환한다() {
        // given
        UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
        UuidCrypto uuidCrypto = mock(UuidCrypto.class);
        UserIdentityResolver resolver = resolverWith(userJpaRepository, uuidCrypto);

        // when
        Long userId = resolver.resolve(1L);

        // then
        assertThat(userId).isNull();
        then(userJpaRepository).should(never()).findIdByPublicUuid(any(UUID.class));
        then(uuidCrypto).should(times(1)).decryptToUuid(1L);
    }

    @Test
    @DisplayName("principal이 UUID면 repository 조회로 userId를 반환한다")
    void principal이_UUID면_repository_조회로_userId를_반환한다() {
        // given
        UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
        UuidCrypto uuidCrypto = mock(UuidCrypto.class);
        UserIdentityResolver resolver = resolverWith(userJpaRepository, uuidCrypto);
        UUID publicUuid = UUID.randomUUID();
        given(userJpaRepository.findIdByPublicUuid(publicUuid)).willReturn(Optional.of(7L));

        // when
        Long userId = resolver.resolve(publicUuid);

        // then
        assertThat(userId).isEqualTo(7L);
        then(userJpaRepository).should(times(1)).findIdByPublicUuid(publicUuid);
        then(uuidCrypto).should(never()).decryptToUuid(any());
    }

    @Test
    @DisplayName("principal이 암호화 문자열이면 복호화 후 조회한다")
    void principal이_암호화_문자열이면_복호화_후_조회한다() {
        // given
        UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
        UuidCrypto uuidCrypto = mock(UuidCrypto.class);
        UserIdentityResolver resolver = resolverWith(userJpaRepository, uuidCrypto);
        UUID publicUuid = UUID.randomUUID();
        String encrypted = "encrypted-principal";
        given(uuidCrypto.decryptToUuid(encrypted)).willReturn(publicUuid);
        given(userJpaRepository.findIdByPublicUuid(publicUuid)).willReturn(Optional.of(9L));

        // when
        Long userId = resolver.resolve(encrypted);

        // then
        assertThat(userId).isEqualTo(9L);
        then(uuidCrypto).should(times(1)).decryptToUuid(encrypted);
        then(userJpaRepository).should(times(1)).findIdByPublicUuid(publicUuid);
    }

    @Test
    @DisplayName("UUID 조회 결과는 캐시되어 같은 UUID 조회 시 repository를 재호출하지 않는다")
    void UUID_조회_결과는_캐시되어_같은_UUID_조회_시_repository를_재호출하지_않는다() {
        // given
        UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
        UuidCrypto uuidCrypto = mock(UuidCrypto.class);
        UserIdentityResolver resolver = resolverWith(userJpaRepository, uuidCrypto);
        UUID publicUuid = UUID.randomUUID();
        String encrypted = "encrypted-principal";
        given(uuidCrypto.decryptToUuid(encrypted)).willReturn(publicUuid);
        given(userJpaRepository.findIdByPublicUuid(publicUuid)).willReturn(Optional.of(11L));

        // when
        Long first = resolver.resolve(encrypted);
        Long second = resolver.resolve(encrypted);

        // then
        assertThat(first).isEqualTo(11L);
        assertThat(second).isEqualTo(11L);
        then(userJpaRepository).should(times(1)).findIdByPublicUuid(publicUuid);
    }

    @Test
    @DisplayName("복호화에 실패하면 null을 반환한다")
    void 복호화에_실패하면_null을_반환한다() {
        // given
        UserJpaRepository userJpaRepository = mock(UserJpaRepository.class);
        UuidCrypto uuidCrypto = mock(UuidCrypto.class);
        UserIdentityResolver resolver = resolverWith(userJpaRepository, uuidCrypto);
        given(uuidCrypto.decryptToUuid("invalid-token")).willReturn(null);

        // when
        Long userId = resolver.resolve("invalid-token");

        // then
        assertThat(userId).isNull();
        then(userJpaRepository).should(never()).findIdByPublicUuid(any(UUID.class));
    }

    @Test
    @DisplayName("레포지토리 빈이 없으면 예외가 발생한다")
    void 레포지토리_빈이_없으면_예외가_발생한다() {
        // given
        @SuppressWarnings("unchecked")
        ObjectProvider<UserJpaRepository> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(null);
        UuidCrypto uuidCrypto = mock(UuidCrypto.class);
        UUID publicUuid = UUID.randomUUID();
        given(uuidCrypto.decryptToUuid("encrypted-principal")).willReturn(publicUuid);
        UserIdentityResolver resolver = new UserIdentityResolver(provider, uuidCrypto);

        // when & then
        assertThatThrownBy(() -> resolver.resolve("encrypted-principal"))
                .isInstanceOf(NullPointerException.class);
    }

    private UserIdentityResolver resolverWith(UserJpaRepository userJpaRepository, UuidCrypto uuidCrypto) {
        @SuppressWarnings("unchecked")
        ObjectProvider<UserJpaRepository> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(userJpaRepository);
        return new UserIdentityResolver(provider, uuidCrypto);
    }
}
