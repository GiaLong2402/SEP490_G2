package com.hbsisms.feature.production.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionOrderUpdateRequest(
        @NotNull @DecimalMin(value = "0.0001", inclusive = true, message = "Số lượng kế hoạch phải > 0")
                BigDecimal plannedQty,
        LocalDate plannedDueDate,
        String note,
        BigDecimal estimatedProductionCost,
        /** Loại khay làm ra. Bỏ trống → giữ loại khay đã chọn. */
        Long variantId) {}
