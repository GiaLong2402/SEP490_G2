package com.hbsisms.feature.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Task 51 — báo hoàn thành sản xuất (+ leftover / hỏng / ngày làm xong / đóng gói 1 kg·0,5 kg). */
public record ProductionOrderCompleteRequest(
        @NotNull @DecimalMin(value = "0.001", inclusive = true, message = "Số lượng thành phẩm phải lớn hơn 0")
                BigDecimal actualQty,
        BigDecimal actualProductionCost,
        /** Trống → chọn variant đang bán đầu tiên của sản phẩm (khi không gửi packs). */
        Long variantId,
        LocalDate finishedDate,
        BigDecimal defectQty,
        String defectReason,
        @Valid List<ProductionLeftoverInput> leftovers,
        /** Chọn/chia quy cách: tổng qty phải = actualQty. Trống → một dòng theo variantId. */
        @Valid List<FinishedPackInput> packs) {}
