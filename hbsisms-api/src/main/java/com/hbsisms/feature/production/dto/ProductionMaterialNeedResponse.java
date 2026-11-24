package com.hbsisms.feature.production.dto;

import com.hbsisms.feature.production.ProductionOrderMaterialLine;
import java.math.BigDecimal;

/** Một dòng NVL trong xem trước / snapshot lệnh (tồn dùng được + phần hết hạn). */
public record ProductionMaterialNeedResponse(
        Long rawMaterialId,
        String rawMaterialCode,
        String rawMaterialName,
        String unit,
        BigDecimal bomQtyPerUnit,
        BigDecimal wastePercent,
        BigDecimal requiredQty,
        BigDecimal onHandQty,
        BigDecimal expiredQty,
        boolean sufficient,
        String stockStatus,
        BigDecimal lineEstimatedCost) {

    public static ProductionMaterialNeedResponse from(ProductionOrderMaterialLine line, BigDecimal expiredQty) {
        var raw = line.getRawMaterial();
        BigDecimal expired = expiredQty == null ? BigDecimal.ZERO : expiredQty;
        return new ProductionMaterialNeedResponse(
                raw.getId(),
                raw.getCode(),
                raw.getName(),
                raw.getUnit(),
                line.getBomQtyPerUnit(),
                line.getWastePercent(),
                line.getRequiredQty(),
                line.getOnHandQty(),
                expired,
                line.isSufficient(),
                line.isSufficient() ? "Đủ" : "Thiếu",
                line.getLineEstimatedCost());
    }
}
