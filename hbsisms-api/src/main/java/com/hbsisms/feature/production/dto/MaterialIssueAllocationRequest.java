package com.hbsisms.feature.production.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Một dòng phân bổ lô khi xác nhận xuất NVL (task 50). */
public record MaterialIssueAllocationRequest(
        @NotNull Long rawMaterialId,
        @NotNull Long lotId,
        @NotNull @DecimalMin(value = "0.001", inclusive = true, message = "Số lượng xuất phải lớn hơn 0")
                BigDecimal qty) {}
