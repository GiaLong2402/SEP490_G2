package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.Size;

/** Body of POST /stocktakes — phạm vi theo kho (+ khu tùy chọn). */
public record StocktakeInput(
        /** Bắt buộc khi có nhiều kho; null = mọi kho active (demo 1 kho). */
        Long warehouseId,
        @Size(max = 60, message = "Khu vực tối đa 60 ký tự") String zone,
        @Size(max = 2000, message = "Ghi chú quá dài") String note) {}
