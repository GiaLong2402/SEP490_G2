package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.Location;

/** Matches FE {@code WarehouseLocation}. */
public record LocationResponse(
        Long id,
        Long warehouseId,
        String code,
        String zone,
        Integer capacity,
        String note,
        long usedQty,
        boolean canDelete) {

    public static LocationResponse from(Location location, long usedQty, boolean canDelete) {
        return new LocationResponse(
                location.getId(),
                location.getWarehouse().getId(),
                location.getCode(),
                location.getZone(),
                location.getCapacity(),
                location.getNote(),
                usedQty,
                canDelete);
    }
}
