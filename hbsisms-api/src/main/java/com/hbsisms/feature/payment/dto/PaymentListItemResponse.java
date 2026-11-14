package com.hbsisms.feature.payment.dto;

import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentReconcileFlag;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.sales.SaleChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Matches FE {@code PaymentListItem}.
 *
 * <p>{@code excessAmount}: tiền khách chuyển thừa (giao dịch gắn "thừa"), để form hoàn tiền điền sẵn.
 * {@code payerName/payerAccount/payerBank}: tài khoản người chuyển do PayOS gửi trong webhook (có thể trống).
 */
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
        String receiverName,
        SaleChannel channel,
        PaymentReconcileFlag reconcileFlag,
        boolean needsRefund,
        BigDecimal excessAmount,
        String payerName,
        String payerAccount,
        String payerBank) {

    public static PaymentListItemResponse from(Payment payment) {
        var order = payment.getOrder();
        var customer = payment.getCustomer();
        var receiver = payment.getReceivedBy();
        PaymentReconcileFlag flag =
                payment.getReconcileFlag() == null ? PaymentReconcileFlag.NONE : payment.getReconcileFlag();
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
                receiver == null ? null : receiver.getFullName(),
                order == null ? null : order.getChannel(),
                flag,
                needsRefund(payment.getRawPayload()),
                flag == PaymentReconcileFlag.OVER ? excess(payment.getRawPayload()) : null,
                webhookText(payment.getRawPayload(), "counterAccountName"),
                webhookText(payment.getRawPayload(), "counterAccountNumber"),
                webhookText(payment.getRawPayload(), "counterAccountBankName"));
    }

    private static BigDecimal excess(Map<String, Object> rawPayload) {
        if (rawPayload == null || !(rawPayload.get("amountMismatch") instanceof Map<?, ?> m)) {
            return null;
        }
        Object value = m.get("excess");
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Đọc rawPayload.webhook.data.{field}; trống → null. */
    private static String webhookText(Map<String, Object> rawPayload, String field) {
        if (rawPayload == null
                || !(rawPayload.get("webhook") instanceof Map<?, ?> webhook)
                || !(webhook.get("data") instanceof Map<?, ?> data)) {
            return null;
        }
        Object value = data.get(field);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    private static boolean needsRefund(Map<String, Object> rawPayload) {
        if (rawPayload == null || rawPayload.get("needsRefund") == null) {
            return false;
        }
        Object value = rawPayload.get("needsRefund");
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
