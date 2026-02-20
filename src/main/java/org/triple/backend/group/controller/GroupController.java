package org.triple.backend.group.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.request.GroupUpdateRequestDto;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.dto.response.GroupUpdateResponseDto;
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
    public GroupCursorResponseDto browsePublicGroups(@RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "10") int size) {
        return groupService.browsePublicGroups(cursor, size);
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
}
