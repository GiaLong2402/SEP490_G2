package com.hbsisms.feature.payment.payos.dto;

import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import java.math.BigDecimal;
import java.time.Instant;

/** Flat body for {@code POST /payments/payos/{payosOrderCode}/link} (FE contract §5). */
public record PayosLinkResponse(
        Long payosOrderCode,
        BigDecimal amount,
        String checkoutUrl,
        String qrCode,
        Instant expiresAt,
        OrderDetailResponse order) {}
