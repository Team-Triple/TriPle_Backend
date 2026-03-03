package org.triple.backend.payment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.payment.dto.request.PaymentCreateReq;
import org.triple.backend.payment.dto.response.PaymentCreateRes;
import org.triple.backend.payment.dto.response.PaymentSearchRes;
import org.triple.backend.payment.service.PaymentService;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @LoginRequired
    @PostMapping("/{invoiceId}")
    public PaymentCreateRes create(@Valid @RequestBody final PaymentCreateReq paymentCreateReq,
                                   @PathVariable final Long invoiceId, @LoginUser Long userId) {
        return paymentService.create(paymentCreateReq, invoiceId, userId);
    }

    @LoginRequired
    @GetMapping("/{invoiceId}")
    public PaymentSearchRes search(@PathVariable final Long invoiceId, @LoginUser final Long userId) {
        return paymentService.search(invoiceId, userId);
    }
}
