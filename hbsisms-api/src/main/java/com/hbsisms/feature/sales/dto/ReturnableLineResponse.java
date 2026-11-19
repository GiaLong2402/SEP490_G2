package com.hbsisms.feature.sales.dto;

import java.math.BigDecimal;

/** One returnable line of an order. */
public record ReturnableLineResponse(
        Long variantId,
        String sku,
        String productName,
        String packLabel,
        BigDecimal unitPrice,
        int maxQty) {}
