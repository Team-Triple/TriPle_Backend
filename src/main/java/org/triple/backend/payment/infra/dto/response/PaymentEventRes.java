package org.triple.backend.payment.infra.dto.response;

sealed public interface PaymentEventRes permits PaymentEventSuccessRes, PaymentEventFailRes{
}
