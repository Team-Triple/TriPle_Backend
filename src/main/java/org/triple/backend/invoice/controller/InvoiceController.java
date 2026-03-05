package org.triple.backend.invoice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.invoice.dto.request.InvoiceAdjustRequestDto;
import org.triple.backend.invoice.dto.request.InvoiceCreateRequestDto;
import org.triple.backend.invoice.dto.request.InvoiceUpdateRequestDto;
import org.triple.backend.invoice.dto.response.InvoiceAdjustResponseDto;
import org.triple.backend.invoice.dto.response.InvoiceCreateResponseDto;
import org.triple.backend.invoice.dto.response.InvoiceDetailResponseDto;
import org.triple.backend.invoice.dto.response.InvoiceUpdateResponseDto;
import org.triple.backend.invoice.mapper.InvoiceUserIdMapper;
import org.triple.backend.invoice.service.InvoiceService;

@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceUserIdMapper invoiceUserIdMapper;

    @LoginRequired
    @PostMapping
    public InvoiceCreateResponseDto create(@LoginUser final Long userId, @RequestBody @Valid InvoiceCreateRequestDto invoiceCreateRequestDto) {
        InvoiceCreateRequestDto decryptedRequest = invoiceUserIdMapper.decryptRecipientUserIds(invoiceCreateRequestDto);
        InvoiceCreateResponseDto response = invoiceService.create(userId, decryptedRequest);
        return invoiceUserIdMapper.encryptRecipientUserIds(response);
    }

    @LoginRequired
    @PatchMapping("/{invoiceId}")
    public InvoiceUpdateResponseDto updateMetaInfo(@LoginUser final Long userId, @PathVariable Long invoiceId, @RequestBody @Valid InvoiceUpdateRequestDto dto) {
        return invoiceService.updateMetaInfo(userId, invoiceId, dto);
    }

    @LoginRequired
    @GetMapping("/travels/{travelItineraryId}")
    public InvoiceDetailResponseDto searchInvoice(
            @LoginUser final Long userId,
            @PathVariable final Long travelItineraryId
    ) {
        InvoiceDetailResponseDto response = invoiceService.searchInvoice(userId, travelItineraryId);
        return invoiceUserIdMapper.encryptUserIds(response);
    }

    @LoginRequired
    @PutMapping("/{invoiceId}")
    public InvoiceAdjustResponseDto updateInfo(@LoginUser final Long userId, @PathVariable Long invoiceId, @RequestBody @Valid InvoiceAdjustRequestDto dto) {
        InvoiceAdjustRequestDto decryptedRequest = invoiceUserIdMapper.decryptRecipientUserIds(dto);
        InvoiceAdjustResponseDto response = invoiceService.updateInfo(userId, invoiceId, decryptedRequest);
        return invoiceUserIdMapper.encryptRecipientUserIds(response);
    }


    @LoginRequired
    @DeleteMapping("/{invoiceId}")
    public void delete(@LoginUser final Long userId, @PathVariable final Long invoiceId) {
        invoiceService.delete(userId, invoiceId);
    }

    @LoginRequired
    @PostMapping("/{invoiceId}/check")
    public void check(@LoginUser final Long userId, @PathVariable final Long invoiceId) {
        invoiceService.check(userId, invoiceId);
    }
}
