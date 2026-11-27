package com.hbsisms.feature.production.dto;

import java.math.BigDecimal;

/**
 * Giá 1 khay của sản phẩm trên lệnh đã hoàn thành: giá đang bán so với giá gợi ý từ giá vốn lệnh này.
 * Chỉ để xem — hệ thống không tự đổi giá đang bán.
 */
public record TrayPriceResponse(
        Long variantId,
        String sku,
        String packLabel,
        Integer unitWeightG,
        boolean active,
        /** Khay vừa làm trong lệnh này. */
        boolean produced,
        BigDecimal listPrice,
        BigDecimal suggestedPrice) {}
