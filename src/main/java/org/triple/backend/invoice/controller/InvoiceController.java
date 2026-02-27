package org.triple.backend.invoice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.invoice.dto.request.InvoiceCreateRequestDto;
import org.triple.backend.invoice.dto.request.InvoiceUpdateRequestDto;
import org.triple.backend.invoice.dto.response.InvoiceCreateResponseDto;
import org.triple.backend.invoice.dto.response.InvoiceUpdateResponseDto;
import org.triple.backend.invoice.service.InvoiceService;

@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @LoginRequired
    @PostMapping
    public InvoiceCreateResponseDto create(@LoginUser final Long userId, @RequestBody @Valid InvoiceCreateRequestDto invoiceCreateRequestDto) {
        return invoiceService.create(userId, invoiceCreateRequestDto);
    }

    @LoginRequired
    @PatchMapping("/{invoiceId}")
    public InvoiceUpdateResponseDto updateMetaInfo(@LoginUser final Long userId, @PathVariable Long invoiceId, @RequestBody @Valid InvoiceUpdateRequestDto dto) {
        return invoiceService.updateMetaInfo(userId, invoiceId, dto);
    }

}
