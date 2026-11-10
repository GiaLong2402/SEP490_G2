package com.hbsisms.feature.storefront.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Update quantity for an existing cart line. */
public record CartItemQtyRequest(
        @NotNull(message = "Số lượng là bắt buộc") @Min(value = 1, message = "Số lượng khay phải ≥ 1")
                Integer qty) {}
