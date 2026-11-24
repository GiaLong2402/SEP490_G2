package com.hbsisms.feature.production.dto;

import com.hbsisms.feature.production.ProductionLeftover;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Khớp FE {@code ProductionLeftover}. */
public record ProductionLeftoverResponse(
        Long rawMaterialId,
        String rawMaterialName,
        String unit,
        BigDecimal qty,
        String lotCode,
        LocalDate expDate,
        BigDecimal amount) {

    public static ProductionLeftoverResponse from(ProductionLeftover row) {
        var material = row.getRawMaterial();
        var lot = row.getLot();
        return new ProductionLeftoverResponse(
                material.getId(),
                material.getName(),
                material.getUnit(),
                row.getQty(),
                lot == null ? null : lot.getLotCode(),
                row.getExpDate(),
                row.getAmount());
    }
}
