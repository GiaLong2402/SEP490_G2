package com.hbsisms.feature.storefront.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Add or set quantity for a cart line. */
public record CartItemRequest(
        @NotNull(message = "variantId là bắt buộc") Long variantId,
        @NotNull(message = "Số lượng là bắt buộc") @Min(value = 1, message = "Số lượng khay phải ≥ 1")
                Integer qty) {}
