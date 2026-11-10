package com.hbsisms.feature.storefront.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** PayOS payment block returned with checkout (real link from task #33). */
public record CheckoutPaymentResponse(
        Long payosOrderCode, BigDecimal amount, String checkoutUrl, String qrCode, Instant expiresAt) {
}
