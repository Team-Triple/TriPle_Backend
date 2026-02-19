package org.triple.backend.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.user.dto.response.UserInfoResponseDto;
import org.triple.backend.user.entity.User;
import org.triple.backend.user.exception.UserErrorCode;
import org.triple.backend.user.repository.UserJpaRepository;

import static org.triple.backend.global.log.MaskUtil.maskId;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserJpaRepository userJpaRepository;

    @Transactional(readOnly = true)
    public UserInfoResponseDto userInfo(final Long userId) {

        log.debug("유저 정보를 받아올 유저의 ID = {}", maskId(userId));
        User savedUser = userJpaRepository.findById(userId).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        log.debug("받아온 유저의 ID = {}", maskId(userId));

        return UserInfoResponseDto.builder()
                .nickname(savedUser.getNickname())
                .gender((savedUser.getGender() != null ? savedUser.getGender().toString() : null))
                .birth(savedUser.getBirth())
                .description(savedUser.getDescription())
                .profileUrl(savedUser.getProfileUrl())
                .build();
    }

}