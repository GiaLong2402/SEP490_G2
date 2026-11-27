package com.hbsisms.feature.production.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionOrderCreateRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.0001", inclusive = true, message = "Số lượng kế hoạch phải > 0")
                BigDecimal plannedQty,
        LocalDate plannedDueDate,
        String note,
        BigDecimal estimatedProductionCost,
        /** Loại khay làm ra (0,5 kg / 1 kg). Bỏ trống → khay 1 kg (hoặc quy cách đầu tiên đang bán). */
        Long variantId) {}
