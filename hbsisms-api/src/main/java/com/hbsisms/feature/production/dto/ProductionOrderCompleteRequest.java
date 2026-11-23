package com.hbsisms.feature.production.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Task 51 — báo hoàn thành sản xuất. */
public record ProductionOrderCompleteRequest(
        @NotNull @DecimalMin(value = "0.001", inclusive = true, message = "Số lượng thành phẩm phải lớn hơn 0")
                BigDecimal actualQty,
        BigDecimal actualProductionCost,
        /** Trống → chọn variant đang bán đầu tiên của sản phẩm. */
        Long variantId) {}
