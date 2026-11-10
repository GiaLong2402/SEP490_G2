package com.hbsisms.feature.storefront.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** PayOS stub (or real) payment block returned with checkout. */
public record CheckoutPaymentResponse(
        Long payosOrderCode, BigDecimal amount, String checkoutUrl, String qrCode, Instant expiresAt) {
}
