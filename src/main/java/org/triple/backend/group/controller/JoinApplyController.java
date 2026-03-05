package org.triple.backend.group.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.group.dto.response.JoinApplyUserResponseDto;
import org.triple.backend.group.entity.joinApply.JoinApplyStatus;
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

    @LoginRequired
    @PostMapping("/{groupId}/join-applies/{joinApplyId}")
    public void joinApplyApprove(@PathVariable final Long groupId, @PathVariable Long joinApplyId, @LoginUser final Long userId) {
        joinApplyService.approve(groupId, userId, joinApplyId);
    }

    @LoginRequired
    @PostMapping("/{groupId}/join-applies/{joinApplyId}/reject")
    public void joinApplyReject(@PathVariable final Long groupId, @PathVariable final Long joinApplyId, @LoginUser final Long userId) {
        joinApplyService.reject(groupId, userId, joinApplyId);
    }

    @LoginRequired
    @GetMapping("/{groupId}/join-applies")
    public JoinApplyUserResponseDto joinApplyUser(@PathVariable final Long groupId,
                                                  @LoginUser final Long userId,
                                                  @RequestParam(required = false) final JoinApplyStatus status,
                                                  @RequestParam(required = false) final Long cursor,
                                                  @RequestParam(defaultValue = "10") final int size) {
        return joinApplyService.joinApplyUser(groupId, userId, status, cursor, size);
    }
}
