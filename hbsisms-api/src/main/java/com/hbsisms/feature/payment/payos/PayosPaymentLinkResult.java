package com.hbsisms.feature.payment.payos;

/** Parsed PayOS {@code POST /v2/payment-requests} success payload. */
public record PayosPaymentLinkResult(
        long orderCode,
        int amount,
        String checkoutUrl,
        String qrCode,
        String paymentLinkId,
        String status) {}
