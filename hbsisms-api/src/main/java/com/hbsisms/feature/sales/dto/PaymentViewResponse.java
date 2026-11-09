package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PayTxnStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Matches FE {@code Payment} nested on order detail. */
public record PaymentViewResponse(
        Long id,
        String code,
        Long orderId,
        Long customerId,
        PaymentMethod method,
        PayDirection direction,
        BigDecimal amount,
        PayTxnStatus status,
        String reference,
        String gateway,
        Long payosOrderCode,
        String checkoutUrl,
        Instant paidAt,
        Long receivedBy,
        String note) {

    public static PaymentViewResponse from(Payment payment) {
        return new PaymentViewResponse(
                payment.getId(),
                payment.getCode(),
                payment.getOrder() == null ? null : payment.getOrder().getId(),
                payment.getCustomer() == null ? null : payment.getCustomer().getId(),
                payment.getMethod(),
                payment.getDirection(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getReference(),
                payment.getGateway(),
                payment.getPayosOrderCode(),
                payment.getCheckoutUrl(),
                payment.getPaidAt(),
                payment.getReceivedBy() == null ? null : payment.getReceivedBy().getId(),
                payment.getNote());
    }
}
