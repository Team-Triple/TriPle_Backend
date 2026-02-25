package org.triple.backend.group.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.request.GroupUpdateRequestDto;
import org.triple.backend.group.dto.response.*;
import org.triple.backend.group.service.GroupService;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @LoginRequired
    @PostMapping
    public CreateGroupResponseDto create(@Valid @RequestBody CreateGroupRequestDto createGroupRequestDto, @LoginUser final Long userId) {
        return groupService.create(createGroupRequestDto, userId);
    }

    @GetMapping
    public GroupCursorResponseDto browsePublicGroups(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Long cursor,
                                                     @RequestParam(defaultValue = "10") int size) {
        return groupService.search(keyword, cursor, size);
    }

    @LoginRequired
    @DeleteMapping("/{groupId}")
    public void delete(@PathVariable Long groupId, @LoginUser final Long userId) {
        groupService.delete(groupId, userId);
    }

    @LoginRequired
    @PatchMapping("/{groupId}")
    public GroupUpdateResponseDto update(@Valid @RequestBody GroupUpdateRequestDto request, @PathVariable Long groupId, @LoginUser final Long userId) {
        return groupService.update(request, groupId, userId);
    }

    @LoginRequired
    @GetMapping("/{groupId}")
    public GroupDetailResponseDto detail(@PathVariable Long groupId, @LoginUser final Long userId) {
        return groupService.detail(groupId, userId);
    }

    @LoginRequired
    @PatchMapping("/{groupId}/owner/{targetUserId}")
    public void transferOwner(@PathVariable Long groupId, @PathVariable Long targetUserId, @LoginUser final Long userId) {
        groupService.ownerTransfer(groupId, targetUserId, userId);
    }

    @LoginRequired
    @DeleteMapping("/{groupId}/users/{targetUserId}")
    public void kick(@PathVariable Long groupId, @PathVariable Long targetUserId, @LoginUser final Long userId) {
        groupService.kick(groupId, userId, targetUserId);
    }

    @LoginRequired
    @DeleteMapping("/{groupId}/users/me")
    public void leave(@PathVariable Long groupId, @LoginUser final Long userId) {
        groupService.leave(groupId, userId);
    }

    @LoginRequired
    @GetMapping("/me")
    public GroupCursorResponseDto myGroups(@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "10") int size, @LoginUser final Long userId) {
        return groupService.myGroups(cursor, size, userId);
    }

    @GetMapping("/{groupId}/menu")
    public GroupMenuResponseDto menu(@PathVariable Long groupId, @LoginUser final Long userId) {
        return groupService.menu(userId, groupId);
    }
}
