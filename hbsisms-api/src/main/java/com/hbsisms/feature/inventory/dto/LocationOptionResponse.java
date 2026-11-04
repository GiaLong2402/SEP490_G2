package com.hbsisms.feature.inventory.dto;

/** Matches FE {@code LocationOption}. */
public record LocationOptionResponse(
        Long id,
        Long warehouseId,
        String code,
        String zone,
        Integer capacity,
        String note,
        String label) {
}
