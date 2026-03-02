package org.triple.backend.payment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.triple.backend.auth.session.LoginRequired;
import org.triple.backend.auth.session.LoginUser;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.dto.response.PaymentConfirmRes;
import org.triple.backend.payment.service.PaymentServiceFacade;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentServiceFacade paymentServiceFacade;

    @LoginRequired
    @PostMapping("/{invoiceId}")
    public PaymentConfirmRes confirm(
        @Valid @RequestBody PaymentConfirmReq paymentConfirmReq,
        @PathVariable Long invoiceId,
        @LoginUser Long userId
    ) {
        return paymentServiceFacade.confirm(paymentConfirmReq, invoiceId, userId);
    }

}
