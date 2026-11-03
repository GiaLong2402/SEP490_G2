package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.Warehouse;
import java.util.List;

/** Matches FE {@code WarehouseDetail}. */
public record WarehouseResponse(
        Long id,
        String code,
        String name,
        String address,
        boolean isActive,
        List<LocationResponse> locations,
        long totalQty) {

    public static WarehouseResponse from(Warehouse warehouse, List<LocationResponse> locations) {
        long totalQty = locations.stream().mapToLong(LocationResponse::usedQty).sum();
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.isActive(),
                locations,
                totalQty);
    }
}
