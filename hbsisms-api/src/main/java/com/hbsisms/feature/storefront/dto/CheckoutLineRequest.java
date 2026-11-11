package com.hbsisms.feature.storefront.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One cart line on POST /checkout. Backend ignores display-only fields from FE. */
public record CheckoutLineRequest(
        @NotNull(message = "variantId là bắt buộc") Long variantId,
        @NotNull(message = "qtyKhay là bắt buộc") @Min(value = 1, message = "Số lượng khay phải ≥ 1") Integer qtyKhay) {
}
