package com.hbsisms.feature.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Snapshot biên lai bán hàng (trang in) — không phải hóa đơn điện tử.
 * Matches FE {@code Receipt}.
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
