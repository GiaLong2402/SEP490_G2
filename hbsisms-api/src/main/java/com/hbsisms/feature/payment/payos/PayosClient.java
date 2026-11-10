package com.hbsisms.feature.payment.payos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hbsisms.common.exception.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** HTTP client for PayOS merchant API (task #33 — create payment link only). */
@Component
public class PayosClient {

    private final RestClient payosRestClient;
    private final PayosProperties properties;
    private final ObjectMapper objectMapper;

    public PayosClient(
            @Qualifier("payosRestClient") RestClient payosRestClient,
            PayosProperties properties,
            ObjectMapper objectMapper) {
        this.payosRestClient = payosRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public PayosPaymentLinkResult createPaymentRequest(
            long orderCode, int amount, String description, long expiredAtEpochSeconds) {
        requireEnabled();

        String returnUrl = properties.returnUrl();
        String cancelUrl = properties.cancelUrl();
        String signature = PayosSignature.signPaymentRequest(
                amount, cancelUrl, description, orderCode, returnUrl, properties.checksumKey());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", description);
        body.put("cancelUrl", cancelUrl);
        body.put("returnUrl", returnUrl);
        body.put("expiredAt", expiredAtEpochSeconds);
        body.put("signature", signature);

        try {
            String raw = payosRestClient
                    .post()
                    .uri("/v2/payment-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(PayosConfig.HEADER_CLIENT_ID, properties.clientId())
                    .header(PayosConfig.HEADER_API_KEY, properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseCreateResponse(raw);
        } catch (RestClientResponseException ex) {
            throw ApiException.badRequest("PayOS từ chối tạo link: HTTP " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Không gọi được PayOS: " + ex.getMessage());
        }
    }

    private PayosPaymentLinkResult parseCreateResponse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw == null ? "{}" : raw);
            String code = text(root, "code");
            if (!"00".equals(code)) {
                String desc = text(root, "desc");
                throw ApiException.badRequest(
                        "PayOS tạo link thất bại"
                                + (desc == null || desc.isBlank() ? "" : ": " + desc));
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw ApiException.badRequest("PayOS không trả data checkout");
            }
            String checkoutUrl = text(data, "checkoutUrl");
            if (checkoutUrl == null || checkoutUrl.isBlank()) {
                throw ApiException.badRequest("PayOS không trả checkoutUrl");
            }
            return new PayosPaymentLinkResult(
                    data.path("orderCode").asLong(),
                    data.path("amount").asInt(),
                    checkoutUrl,
                    text(data, "qrCode"),
                    text(data, "paymentLinkId"),
                    text(data, "status"));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("Không đọc được phản hồi PayOS");
        }
    }

    /** Flatten useful fields for {@code Payment.rawPayload}. */
    public static Map<String, Object> toRawPayload(PayosPaymentLinkResult result, Instant expiresAt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("qrCode", result.qrCode() == null ? "" : result.qrCode());
        map.put("paymentLinkId", result.paymentLinkId());
        map.put("status", result.status());
        map.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
        map.put("stub", false);
        return map;
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PayOS chưa cấu hình");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
