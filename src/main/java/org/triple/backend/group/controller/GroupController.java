package org.triple.backend.group.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.request.GroupUpdateRequestDto;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.dto.response.GroupDetailResponseDto;
import org.triple.backend.group.dto.response.GroupUpdateResponseDto;
import org.triple.backend.group.service.GroupService;

@Validated
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
    public GroupCursorResponseDto browsePublicGroups(@RequestParam(required = false) @Size(max = 20, message = "검색어는 최대 입력 문자 수를 초과했습니다.") String keyword,
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
}
