package com.hbsisms.feature.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One line of POST /returns. */
public record ReturnLineInput(
        @NotNull(message = "variantId là bắt buộc") Long variantId,
        Long lotId,
        @Min(value = 0, message = "Số khay trả không hợp lệ") int qty) {}
