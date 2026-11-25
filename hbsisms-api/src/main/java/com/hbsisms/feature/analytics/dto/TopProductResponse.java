package com.hbsisms.feature.analytics.dto;

import java.math.BigDecimal;

/** One ranked product/variant row (Inter 4 — Cường). */
public record TopProductResponse(
        Long variantId,
        String productName,
        String sku,
        String packLabel,
        long qtyKhay,
        BigDecimal revenue,
        BigDecimal profit) {}
