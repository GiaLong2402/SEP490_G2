package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Matches FE {@code AdjustInput}. */
public record AdjustStockRequest(
        @NotNull(message = "Dòng tồn là bắt buộc") Long stockId,
        @NotNull(message = "Số lượng điều chỉnh là bắt buộc") Integer qtyDelta,
        @NotBlank(message = "Vui lòng nhập lý do điều chỉnh") String note) {
}
