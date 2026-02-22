package org.triple.backend.group.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.group.service.JoinApplyService;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class JoinApplyController {

    private final JoinApplyService joinApplyService;

    @LoginRequired
    @PostMapping("/{groupId}/join-applies")
    public void joinApply(@PathVariable final Long groupId, @LoginUser final Long userId) {
        joinApplyService.joinApply(groupId, userId);
    }
}
