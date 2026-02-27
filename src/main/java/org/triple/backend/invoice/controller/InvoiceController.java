package org.triple.backend.invoice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.invoice.dto.request.InvoiceCreateRequestDto;
import org.triple.backend.invoice.dto.response.InvoiceCreateResponseDto;
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
    @DeleteMapping("/{invoiceId}")
    public void delete(@LoginUser final Long userId, @PathVariable final Long invoiceId) {
        invoiceService.delete(userId, invoiceId);
    }
}
