package org.triple.backend.group.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.triple.backend.group.dto.request.CreateGroupRequestDto;
import org.triple.backend.group.dto.response.CreateGroupResponseDto;
import org.triple.backend.group.service.GroupService;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public CreateGroupResponseDto create(@Valid @RequestBody CreateGroupRequestDto createGroupRequestDto) {
        return groupService.create(createGroupRequestDto);
    }
}