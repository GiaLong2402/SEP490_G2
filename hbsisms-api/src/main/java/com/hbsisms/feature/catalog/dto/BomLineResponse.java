package com.hbsisms.feature.catalog.dto;

import com.hbsisms.feature.catalog.ProductBomLine;
import java.math.BigDecimal;

public record BomLineResponse(
        Long id,
        Long rawMaterialId,
        String rawMaterialCode,
        String rawMaterialName,
        Long componentProductId,
        String componentProductCode,
        String componentProductName,
        BigDecimal qty,
        Long unitId,
        String unitCode,
        String unitName,
        BigDecimal wastePercent,
        int sortOrder) {

    public static BomLineResponse from(ProductBomLine line) {
        var raw = line.getRawMaterial();
        var component = line.getComponentProduct();
        var unit = line.getUnit();
        return new BomLineResponse(
                line.getId(),
                raw == null ? null : raw.getId(),
                raw == null ? null : raw.getCode(),
                raw == null ? null : raw.getName(),
                component == null ? null : component.getId(),
                component == null ? null : component.getCode(),
                component == null ? null : component.getName(),
                line.getQty(),
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                line.getWastePercent(),
                line.getSortOrder());
    }
}
