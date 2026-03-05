package org.triple.backend.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.triple.backend.global.error.BusinessException;
import org.triple.backend.payment.dto.request.PaymentConfirmReq;
import org.triple.backend.payment.dto.response.PaymentConfirmRes;
import org.triple.backend.payment.entity.Payment;
import org.triple.backend.payment.entity.PaymentStatus;
import org.triple.backend.payment.exception.PaymentErrorCode;
import org.triple.backend.payment.infra.exception.ConfirmAnonymousException;
import org.triple.backend.payment.infra.exception.ConfirmRecoverFailedException;
import org.triple.backend.payment.infra.exception.ConfirmServerException;
import org.triple.backend.payment.infra.dto.response.ConfirmResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceFacade {
    private final PaymentService paymentService;

    public PaymentConfirmRes confirm(PaymentConfirmReq paymentConfirmReq, Long invoiceId, Long userId) {
        Payment payment = paymentService.readyToInProgressPayment(paymentConfirmReq, invoiceId, userId);

        try{
            ConfirmResponse confirmRes = paymentService.confirm(payment);

            Payment donePayment = paymentService.inprogressToDonePayment(confirmRes);

            return PaymentConfirmRes.from(donePayment);
        } catch (ConfirmRecoverFailedException e) {
            log.error("재시도 3회 실패, 네트워크 에러 발생", e);
            recoverFailed(paymentConfirmReq);
            throw new BusinessException(PaymentErrorCode.CONFIRM_FAILED);
        } catch (ConfirmAnonymousException e) {
            log.error("알 수 없는 에러 발생", e);
            recoverFailed(paymentConfirmReq);
            throw new BusinessException(PaymentErrorCode.CONFIRM_FAILED);
        } catch (ConfirmServerException e) {
            log.error("토스 서버 에러 발생", e);
            recoverFailed(paymentConfirmReq);
            throw new BusinessException(PaymentErrorCode.CONFIRM_FAILED);
        } catch (DataAccessException e) {
            log.error("승인은 성공했으나 DB 동기화 실패");
            recoverFailed(paymentConfirmReq);
            throw new BusinessException(PaymentErrorCode.CONFIRM_FAILED);
        }
    }

    private void recoverFailed(PaymentConfirmReq paymentConfirmReq) {
        paymentService.failConfirm(paymentConfirmReq, PaymentStatus.RETRY_FAILED);
    }
}
