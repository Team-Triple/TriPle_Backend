package org.triple.backend.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.user.dto.response.UserInfoResponseDto;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserJpaRepository userJpaRepository;

    @Transactional(readOnly = true)
    public UserInfoResponseDto userInfo(final Long userId) {

        User savedUser = userJpaRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return UserInfoResponseDto.builder()
                .nickname(savedUser.getNickname())
                .gender(savedUser.getGender().toString())
                .birth(savedUser.getBirth())
                .description(savedUser.getDescription())
                .profileUrl(savedUser.getProfileUrl())
                .build();
    }

}