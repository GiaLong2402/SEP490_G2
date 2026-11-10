package com.hbsisms.feature.payment.payos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * PayOS merchant credentials (task #32). Bound from {@code app.payment.payos.*} / {@code PAYOS_*}.
 * Used by create-link / webhook in later tasks — no HTTP calls here.
 */
@ConfigurationProperties(prefix = "app.payment.payos")
public record PayosProperties(
        /** {@code off} | {@code sandbox} | {@code live}. */
        String mode,
        String clientId,
        String apiKey,
        String checksumKey,
        String baseUrl,
        String returnUrl,
        String cancelUrl) {

    /**
     * True when mode is sandbox or live and Client ID, API Key, and Checksum Key are all set.
     */
    public boolean enabled() {
        if (!StringUtils.hasText(mode)) {
            return false;
        }
        String normalized = mode.trim().toLowerCase();
        if (!"sandbox".equals(normalized) && !"live".equals(normalized)) {
            return false;
        }
        return StringUtils.hasText(clientId)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(checksumKey);
    }
}
