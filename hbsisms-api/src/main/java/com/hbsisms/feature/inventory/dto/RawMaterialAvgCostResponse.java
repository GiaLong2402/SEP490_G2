package com.hbsisms.feature.inventory.dto;

import java.math.BigDecimal;

/** ĐG dùng khi xuất SX: WAC NVL nếu có, không thì null (FE fallback giá lô). */
public record RawMaterialAvgCostResponse(Long rawMaterialId, BigDecimal avgUnitCost, String source) {}
