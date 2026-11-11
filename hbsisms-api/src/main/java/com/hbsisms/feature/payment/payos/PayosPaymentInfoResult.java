package com.hbsisms.feature.payment.payos;

/** Parsed PayOS GET /v2/payment-requests/{orderCode} payload. */
public record PayosPaymentInfoResult(long orderCode, int amount, String status, String paymentLinkId) {}
