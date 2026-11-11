package com.hbsisms.feature.payment.payos.dto;

/** Minimal 2xx body so PayOS stops retrying. */
public record PayosWebhookAck(boolean success) {
    public static PayosWebhookAck ok() {
        return new PayosWebhookAck(true);
    }
}
