package com.hbsisms.feature.catalog.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** One volume-tier row inside {@link VariantRequest} — matches FE tier object. */
public record TierInput(
        @NotNull(message = "Số khay tối thiểu là bắt buộc") Integer minQtyKhay,
        @NotNull(message = "Đơn giá bậc là bắt buộc") BigDecimal unitPrice) {
}
