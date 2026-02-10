package org.triple.backend.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.triple.backend.auth.LoginUser;
import org.triple.backend.user.dto.response.UserInfoResponseDto;
import org.triple.backend.user.service.UserService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserInfoResponseDto userInfo(@LoginUser Long userId) {
        return userService.userInfo(userId);
    }
}
