package com.hbsisms.feature.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Snapshot biên lai bán hàng. Matches FE {@code Receipt} (POS).
 */
public record OrderReceiptResponse(
        Long orderId,
        String code,
        Instant placedAt,
        String customerName,
        String cashierName,
        String paymentMethod,
        List<OrderReceiptLineResponse> lines,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal total,
        BigDecimal tendered,
        BigDecimal change,
        BigDecimal debtAfter) {}
