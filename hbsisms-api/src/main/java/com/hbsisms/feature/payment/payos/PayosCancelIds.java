package com.hbsisms.feature.payment.payos;

import com.hbsisms.feature.payment.Payment;

/** Resolves PayOS cancel path id (paymentLinkId preferred, else orderCode). */
public final class PayosCancelIds {

    private PayosCancelIds() {}

    public static String from(Payment payment) {
        return from(payment, null);
    }

    /** Prefer {@code info.paymentLinkId} from a fresh GET when present. */
    public static String from(Payment payment, PayosPaymentInfoResult info) {
        if (info != null && info.paymentLinkId() != null && !info.paymentLinkId().isBlank()) {
            return info.paymentLinkId();
        }
        if (payment != null && payment.getRawPayload() != null && payment.getRawPayload().get("paymentLinkId") != null) {
            String linkId = String.valueOf(payment.getRawPayload().get("paymentLinkId")).trim();
            if (!linkId.isBlank() && !"null".equalsIgnoreCase(linkId)) {
                return linkId;
            }
        }
        if (payment == null || payment.getPayosOrderCode() == null) {
            return "";
        }
        return String.valueOf(payment.getPayosOrderCode());
    }
}
