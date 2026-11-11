package com.hbsisms.feature.sales.dto;

import java.math.BigDecimal;

/** One line on sales receipt. Matches FE {@code Receipt.lines[]}. */
public record OrderReceiptLineResponse(
        Long variantId,
        String productName,
        String packLabel,
        String qtyLabel,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {}
