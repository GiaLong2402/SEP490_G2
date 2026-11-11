package com.hbsisms.feature.storefront.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        Long id,
        UUID sessionToken,
        String status,
        List<CartItemResponse> items,
        int totalQty,
        BigDecimal totalAmount) {}
