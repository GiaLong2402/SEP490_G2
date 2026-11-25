package com.hbsisms.feature.sales.dto;

import java.util.List;

/** Nhóm theo vị trí kho. Khớp FE {@code DailyPickingGroup}. */
public record DailyPickingGroupResponse(
        Long warehouseId,
        String warehouseCode,
        Long locationId,
        String locationCode,
        String zone,
        int totalKhay,
        List<DailyPickingLineResponse> lines) {}
