package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.RawMaterial;
import java.math.BigDecimal;

/** Matches FE {@code RawMaterial}. */
public record RawMaterialResponse(
        Long id,
        String code,
        String name,
        String unit,
        BigDecimal qtyOnHand,
        BigDecimal reorderLevel,
        boolean isActive) {

    public static RawMaterialResponse from(RawMaterial material) {
        return new RawMaterialResponse(
                material.getId(),
                material.getCode(),
                material.getName(),
                material.getUnit(),
                material.getQtyOnHand(),
                material.getReorderLevel(),
                material.isActive());
    }
}
