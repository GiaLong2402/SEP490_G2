package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Matches FE {@code MoveInput}. */
public record MoveStockRequest(
        @NotNull(message = "Dòng tồn là bắt buộc") Long stockId,
        @NotNull(message = "Vị trí đích là bắt buộc") Long toLocationId,
        @NotNull(message = "Số lượng là bắt buộc")
                @Min(value = 1, message = "Số lượng phải là số nguyên dương")
                Integer qty) {
}
