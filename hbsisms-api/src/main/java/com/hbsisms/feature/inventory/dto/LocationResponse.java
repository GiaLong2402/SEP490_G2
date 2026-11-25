package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.Location;

/** Matches FE {@code WarehouseLocation}. */
public record LocationResponse(
        Long id,
        Long warehouseId,
        String code,
        String zone,
        int capacity,
        String note,
        long usedQty,
        boolean canDelete,
        /** Kho nguyên liệu: số nguyên liệu cất ở khu này. Kho thành phẩm: 0. */
        long materialCount) {

    public static LocationResponse from(Location location, long usedQty, boolean canDelete) {
        return from(location, usedQty, canDelete, 0);
    }

    public static LocationResponse from(Location location, long usedQty, boolean canDelete, long materialCount) {
        return new LocationResponse(
                location.getId(),
                location.getWarehouse().getId(),
                location.getCode(),
                location.getZone(),
                location.getCapacity(),
                location.getNote(),
                usedQty,
                canDelete,
                materialCount);
    }
}
