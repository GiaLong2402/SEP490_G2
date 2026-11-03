package com.hbsisms.feature.customer.pricing.dto;

import com.hbsisms.feature.sales.SellUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Một dòng trong POST /pricing/resolve. Matches FE {@code PricingLineInput}. */
public record PricingLineInput(
        @NotNull(message = "variantId là bắt buộc")
        Long variantId,

        @NotNull(message = "unit là bắt buộc")
        SellUnit unit,

        @Min(value = 1, message = "Số lượng phải là số nguyên dương")
        int qty,

        /** Có giá trị khi nhân viên nhập tay, ghi đè giá hệ thống. */
        @DecimalMin(value = "0", message = "Giá nhập tay không được âm")
        BigDecimal manualPrice) {
}
