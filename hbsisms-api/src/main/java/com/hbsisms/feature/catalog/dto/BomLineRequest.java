package com.hbsisms.feature.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** One line in PUT /products/{id}/bom. Exactly one of rawMaterialId / componentProductId. */
public record BomLineRequest(
        Long rawMaterialId,
        Long componentProductId,

        @NotNull(message = "Vui lòng nhập số lượng định mức")
        @DecimalMin(value = "0.0001", message = "Số lượng định mức phải lớn hơn 0")
        @Digits(integer = 10, fraction = 4, message = "Số lượng định mức không hợp lệ")
        BigDecimal qty,

        @NotNull(message = "Vui lòng chọn đơn vị tính")
        Long unitId,

        /** Null or 0–100. */
        @Digits(integer = 3, fraction = 2, message = "% hao hụt không hợp lệ")
        BigDecimal wastePercent) {
}
