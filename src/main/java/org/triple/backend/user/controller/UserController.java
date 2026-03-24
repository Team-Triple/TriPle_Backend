package org.triple.backend.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.triple.backend.auth.jwt.LoginRequired;
import org.triple.backend.auth.jwt.LoginUser;
import org.triple.backend.user.dto.response.UpdateUserInfoRes;
import org.triple.backend.user.dto.request.UpdateUserInfoReq;
import org.triple.backend.user.dto.response.UserInfoResponseDto;
import org.triple.backend.user.service.UserService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @LoginRequired
    @GetMapping("/me")
    public UserInfoResponseDto userInfo(@LoginUser Long userId) {
        return userService.userInfo(userId);
    }

    @LoginRequired
    @PatchMapping
    public UpdateUserInfoRes updateUserInfo(@LoginUser Long userId, @RequestBody UpdateUserInfoReq updateUserInfoReq) {
        return userService.updateUserInfo(userId, updateUserInfoReq);
    }
}
