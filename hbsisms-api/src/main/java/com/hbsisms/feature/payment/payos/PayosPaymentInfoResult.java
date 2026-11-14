package com.hbsisms.feature.payment.payos;

/**
 * Parsed PayOS GET /v2/payment-requests/{orderCode} payload.
 *
 * @param amount     số tiền của link (= số tiền đơn)
 * @param amountPaid số tiền khách thực chuyển; null nếu PayOS không trả
 */
public record PayosPaymentInfoResult(
        long orderCode, int amount, String status, String paymentLinkId, Integer amountPaid) {

    public PayosPaymentInfoResult(long orderCode, int amount, String status, String paymentLinkId) {
        this(orderCode, amount, status, paymentLinkId, null);
    }

    /** Số tiền thực nhận: ưu tiên amountPaid; PayOS không trả thì coi như đúng số tiền link. */
    public int received() {
        return amountPaid != null && amountPaid > 0 ? amountPaid : amount;
    }
}
