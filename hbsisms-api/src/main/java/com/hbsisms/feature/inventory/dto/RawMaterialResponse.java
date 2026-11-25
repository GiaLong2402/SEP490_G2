package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.RawMaterial;
import java.math.BigDecimal;

/** Matches FE {@code RawMaterial}. */
public record RawMaterialResponse(
        Long id,
        String code,
        String name,
        String unit,
        Long unitId,
        BigDecimal qtyOnHand,
        BigDecimal reorderLevel,
        boolean isActive,
        Long storageLocationId,
        String storageLocationCode,
        String storageLocationName) {

    public static RawMaterialResponse from(RawMaterial material) {
        return new RawMaterialResponse(
                material.getId(),
                material.getCode(),
                material.getName(),
                material.getUnit(),
                material.getUnitRef() == null ? null : material.getUnitRef().getId(),
                material.getQtyOnHand(),
                material.getReorderLevel(),
                material.isActive(),
                material.getStorageLocation() == null ? null : material.getStorageLocation().getId(),
                material.getStorageLocation() == null ? null : material.getStorageLocation().getCode(),
                material.getStorageLocation() == null ? null : material.getStorageLocation().getZone());
    }
}
