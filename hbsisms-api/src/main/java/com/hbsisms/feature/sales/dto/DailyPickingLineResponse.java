package com.hbsisms.feature.sales.dto;

import java.time.LocalDate;

/** Dòng trong bản gộp theo ngày. Khớp FE {@code DailyPickingLine}. */
public record DailyPickingLineResponse(
        Long orderId,
        String orderCode,
        Long variantId,
        String productName,
        String sku,
        String packLabel,
        Long lotId,
        String lotCode,
        LocalDate expDate,
        int qtyKhay) {}
