package org.triple.backend.transfer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.jwt.LoginRequired;
import org.triple.backend.auth.jwt.LoginUser;
import org.triple.backend.transfer.dto.request.TransferAdjustRequestDto;
import org.triple.backend.transfer.dto.request.TransferCreateRequestDto;
import org.triple.backend.transfer.dto.request.TransferUpdateRequestDto;
import org.triple.backend.transfer.dto.response.TransferAdjustResponseDto;
import org.triple.backend.transfer.dto.response.TransferCreateResponseDto;
import org.triple.backend.transfer.dto.response.TransferDetailResponseDto;
import org.triple.backend.transfer.dto.response.TransferUpdateResponseDto;
import org.triple.backend.transfer.mapper.TransferUserIdMapper;
import org.triple.backend.transfer.service.TransferService;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final TransferUserIdMapper transferUserIdMapper;

    @LoginRequired
    @PostMapping
    public TransferCreateResponseDto create(@LoginUser final Long userId, @RequestBody @Valid TransferCreateRequestDto transferCreateRequestDto) {
        TransferCreateRequestDto decryptedRequest = transferUserIdMapper.decryptRecipientUserIds(transferCreateRequestDto);
        TransferCreateResponseDto response = transferService.create(userId, decryptedRequest);
        return transferUserIdMapper.encryptRecipientUserIds(response);
    }

    @LoginRequired
    @PatchMapping("/{transferId}")
    public TransferUpdateResponseDto updateMetaInfo(@LoginUser final Long userId, @PathVariable Long transferId, @RequestBody @Valid TransferUpdateRequestDto dto) {
        return transferService.updateMetaInfo(userId, transferId, dto);
    }

    @LoginRequired
    @GetMapping("/travels/{travelItineraryId}")
    public TransferDetailResponseDto searchTransfer(
            @LoginUser final Long userId,
            @PathVariable final Long travelItineraryId
    ) {
        TransferDetailResponseDto response = transferService.searchTransfer(userId, travelItineraryId);
        return transferUserIdMapper.encryptUserIds(response);
    }

    @LoginRequired
    @PutMapping("/{transferId}")
    public TransferAdjustResponseDto updateInfo(@LoginUser final Long userId, @PathVariable Long transferId, @RequestBody @Valid TransferAdjustRequestDto dto) {
        TransferAdjustRequestDto decryptedRequest = transferUserIdMapper.decryptRecipientUserIds(dto);
        TransferAdjustResponseDto response = transferService.updateInfo(userId, transferId, decryptedRequest);
        return transferUserIdMapper.encryptRecipientUserIds(response);
    }


    @LoginRequired
    @DeleteMapping("/{transferId}")
    public void delete(@LoginUser final Long userId, @PathVariable final Long transferId) {
        transferService.delete(userId, transferId);
    }

    @LoginRequired
    @PostMapping("/{transferId}/check")
    public void check(@LoginUser final Long userId, @PathVariable final Long transferId) {
        transferService.check(userId, transferId);
    }

    @LoginRequired
    @PatchMapping("/{transferId}/users/me/done")
    public void completeMyTransfer(@LoginUser final Long userId, @PathVariable final Long transferId) {
        transferService.completeMyTransfer(userId, transferId);
    }
}
