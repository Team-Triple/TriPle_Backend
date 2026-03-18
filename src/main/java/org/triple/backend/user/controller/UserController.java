package org.triple.backend.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
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
    public void updateUserInfo(@LoginUser Long userId, @RequestBody UpdateUserInfoReq updateUserInfoReq) {
        userService.updateUserInfo(userId, updateUserInfoReq);
    }
}
