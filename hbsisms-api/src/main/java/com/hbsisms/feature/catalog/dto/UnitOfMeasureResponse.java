package com.hbsisms.feature.catalog.dto;

import com.hbsisms.feature.catalog.UnitOfMeasure;

/** Matches FE {@code UnitOfMeasure}. */
public record UnitOfMeasureResponse(Long id, String code, String name, boolean isActive) {

    public static UnitOfMeasureResponse from(UnitOfMeasure unit) {
        return new UnitOfMeasureResponse(unit.getId(), unit.getCode(), unit.getName(), unit.isActive());
    }
}
