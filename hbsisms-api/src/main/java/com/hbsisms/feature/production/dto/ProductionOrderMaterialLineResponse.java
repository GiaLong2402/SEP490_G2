package com.hbsisms.feature.production.dto;

import com.hbsisms.feature.production.ProductionOrderMaterialLine;
import java.math.BigDecimal;

public record ProductionOrderMaterialLineResponse(
        Long id,
        Long rawMaterialId,
        String rawMaterialCode,
        String rawMaterialName,
        String unit,
        BigDecimal bomQtyPerUnit,
        BigDecimal wastePercent,
        BigDecimal requiredQty,
        BigDecimal onHandQty,
        boolean sufficient,
        String stockStatus,
        BigDecimal lineEstimatedCost) {

    public static ProductionOrderMaterialLineResponse from(ProductionOrderMaterialLine line) {
        var raw = line.getRawMaterial();
        return new ProductionOrderMaterialLineResponse(
                line.getId(),
                raw.getId(),
                raw.getCode(),
                raw.getName(),
                raw.getUnit(),
                line.getBomQtyPerUnit(),
                line.getWastePercent(),
                line.getRequiredQty(),
                line.getOnHandQty(),
                line.isSufficient(),
                line.isSufficient() ? "Đủ" : "Thiếu",
                line.getLineEstimatedCost());
    }
}
