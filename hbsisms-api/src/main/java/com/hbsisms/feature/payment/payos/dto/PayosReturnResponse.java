package com.hbsisms.feature.payment.payos.dto;

import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import java.math.BigDecimal;
import java.time.Instant;

/** Flat body for {@code GET /payments/payos/return} (FE contract §5). */
public record PayosReturnResponse(
        Long payosOrderCode,
        String status,
        BigDecimal amount,
        Instant paidAt,
        OrderDetailResponse order) {}
