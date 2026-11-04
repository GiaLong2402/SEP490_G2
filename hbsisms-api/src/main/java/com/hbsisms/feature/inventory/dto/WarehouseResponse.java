package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.Warehouse;
import java.util.List;

/** Matches FE {@code WarehouseDetail}. */
public record WarehouseResponse(
        Long id,
        String code,
        String name,
        String address,
        int capacity,
        boolean isActive,
        List<LocationResponse> locations,
        long totalQty,
        long allocatedCapacity) {

    public static WarehouseResponse from(Warehouse warehouse, List<LocationResponse> locations) {
        long totalQty = locations.stream().mapToLong(LocationResponse::usedQty).sum();
        long allocatedCapacity = locations.stream().mapToLong(LocationResponse::capacity).sum();
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getCapacity(),
                warehouse.isActive(),
                locations,
                totalQty,
                allocatedCapacity);
    }
}
