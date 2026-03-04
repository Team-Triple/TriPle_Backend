package org.triple.backend.payment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.dto.request.PaymentCreateReq;
import org.triple.backend.payment.dto.response.PaymentConfirmRes;
import org.triple.backend.payment.dto.response.PaymentCreateRes;
import org.triple.backend.payment.dto.response.PaymentCursorRes;
import org.triple.backend.payment.service.PaymentService;
import org.triple.backend.payment.service.PaymentServiceFacade;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentServiceFacade paymentServiceFacade;
    private final PaymentService paymentService;

    @LoginRequired
    @PostMapping("/{invoiceId}")
    public PaymentCreateRes create(@Valid @RequestBody final PaymentCreateReq paymentCreateReq,
                                   @PathVariable final Long invoiceId, @LoginUser Long userId) {
        return paymentService.create(paymentCreateReq, invoiceId, userId);
    }

    @LoginRequired
    @PostMapping("/{invoiceId}/confirm")
    public PaymentConfirmRes confirm(
            @Valid @RequestBody PaymentConfirmReq paymentConfirmReq,
            @PathVariable Long invoiceId,
            @LoginUser Long userId
    ) {
        return paymentServiceFacade.confirm(paymentConfirmReq, invoiceId, userId);
    }

    @LoginRequired
    @GetMapping
    public PaymentCursorRes search(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) Long cursor,
                                   @RequestParam(defaultValue = "10") int size,
                                   @LoginUser final Long userId) {
        return paymentService.search(keyword, cursor, size, userId);
    }
}
