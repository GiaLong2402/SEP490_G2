package com.hbsisms.feature.production.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Một dòng NVL dư trả lại kho khi báo hoàn thành SX. */
public record ProductionLeftoverInput(
        @NotNull Long rawMaterialId,
        @NotNull @DecimalMin(value = "0.001", inclusive = true) BigDecimal qty,
        @NotNull LocalDate expDate) {}
