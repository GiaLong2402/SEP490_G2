package com.hbsisms.feature.payment.dto;

import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PayTxnStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Matches FE {@code PaymentListItem}. */
public record PaymentListItemResponse(
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
        String note,
        String orderCode,
        String customerName,
        String receiverName) {

    public static PaymentListItemResponse from(Payment payment) {
        var order = payment.getOrder();
        var customer = payment.getCustomer();
        var receiver = payment.getReceivedBy();
        return new PaymentListItemResponse(
                payment.getId(),
                payment.getCode(),
                order == null ? null : order.getId(),
                customer == null ? null : customer.getId(),
                payment.getMethod(),
                payment.getDirection(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getReference(),
                payment.getGateway(),
                payment.getPayosOrderCode(),
                payment.getCheckoutUrl(),
                payment.getPaidAt(),
                receiver == null ? null : receiver.getId(),
                payment.getNote(),
                order == null ? null : order.getCode(),
                customer == null ? (order == null ? null : "Khách lẻ") : customer.getName(),
                receiver == null ? null : receiver.getFullName());
    }
}
