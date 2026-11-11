package com.hbsisms.feature.payment.payos;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 helpers for PayOS (create-link #33 + webhook #34).
 *
 * @see <a href="https://payos.vn/docs/api/">payOS API</a>
 * @see <a href="https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature.md">webhook signature</a>
 */
public final class PayosSignature {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * Canonical query string for webhook {@code data} object (keys sorted alphabetically).
     * Null / "null" / "undefined" → empty; nested arrays → JSON after sorting each element's keys.
     */
    public static String dataObjectQueryString(Map<String, ?> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        Map<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> e : data.entrySet()) {
            if (e.getKey() != null) {
                sorted.put(e.getKey(), e.getValue());
            }
        }
        List<String> parts = new ArrayList<>(sorted.size());
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            parts.add(e.getKey() + "=" + valueAsString(e.getValue()));
        }
        return String.join("&", parts);
    }

    /** HMAC hex over webhook {@code data} map. */
    public static String signDataObject(Map<String, ?> data, String checksumKey) {
        return hmacHex(dataObjectQueryString(data), checksumKey);
    }

    /** Constant-time compare of computed vs provided webhook signature. */
    public static boolean verifyWebhook(Map<String, ?> data, String signature, String checksumKey) {
        if (signature == null || signature.isBlank() || checksumKey == null || checksumKey.isBlank()) {
            return false;
        }
        String expected = signDataObject(data, checksumKey);
        return constantTimeEquals(expected, signature.trim());
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

    private static String valueAsString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            if ("undefined".equals(s) || "null".equals(s)) {
                return "";
            }
            return s;
        }
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            try {
                return MAPPER.writeValueAsString(sortMapKeys(map));
            } catch (Exception ex) {
                return String.valueOf(value);
            }
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object ele : list) {
                if (ele instanceof Map<?, ?> m) {
                    normalized.add(sortMapKeys(m));
                } else {
                    normalized.add(ele);
                }
            }
            try {
                return MAPPER.writeValueAsString(normalized);
            } catch (Exception ex) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private static Map<String, Object> sortMapKeys(Map<?, ?> map) {
        Map<String, Object> sorted = new LinkedHashMap<>();
        map.keySet().stream()
                .map(String::valueOf)
                .sorted()
                .forEach(k -> sorted.put(k, map.get(k)));
        return sorted;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
