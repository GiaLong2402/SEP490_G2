package com.hbsisms.feature.payment.payos;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 helpers for PayOS payment-request signatures (task #33).
 *
 * @see <a href="https://payos.vn/docs/api/">payOS API</a>
 */
public final class PayosSignature {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private PayosSignature() {}

    /**
     * Signature for {@code POST /v2/payment-requests}: keys alphabetically
     * {@code amount}, {@code cancelUrl}, {@code description}, {@code orderCode}, {@code returnUrl}.
     */
    public static String signPaymentRequest(
            long amount, String cancelUrl, String description, long orderCode, String returnUrl, String checksumKey) {
        String data = "amount="
                + amount
                + "&cancelUrl="
                + nullToEmpty(cancelUrl)
                + "&description="
                + nullToEmpty(description)
                + "&orderCode="
                + orderCode
                + "&returnUrl="
                + nullToEmpty(returnUrl);
        return hmacHex(data, checksumKey);
    }

    public static String hmacHex(String data, String checksumKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Không tạo được chữ ký PayOS", ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
