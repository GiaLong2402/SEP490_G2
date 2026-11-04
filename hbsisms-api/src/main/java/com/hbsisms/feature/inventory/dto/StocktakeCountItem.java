package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One counted row in PUT /stocktakes/{id}/lines. */
public record StocktakeCountItem(
        @NotNull(message = "lineId là bắt buộc") Long lineId,
        @Min(value = 0, message = "Số đếm không được âm") Integer countedQty) {}
