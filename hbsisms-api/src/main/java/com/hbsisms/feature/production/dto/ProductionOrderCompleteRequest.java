package com.hbsisms.feature.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Task 51 — báo hoàn thành sản xuất (+ leftover / hỏng / ngày làm xong). */
public record ProductionOrderCompleteRequest(
        @NotNull @DecimalMin(value = "0.001", inclusive = true, message = "Số lượng thành phẩm phải lớn hơn 0")
                BigDecimal actualQty,
        BigDecimal actualProductionCost,
        /** Trống → chọn variant đang bán đầu tiên của sản phẩm. */
        Long variantId,
        LocalDate finishedDate,
        BigDecimal defectQty,
        String defectReason,
        @Valid List<ProductionLeftoverInput> leftovers) {}
