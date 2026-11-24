package com.hbsisms.feature.production.dto;

import java.math.BigDecimal;
import java.util.List;

/** Xem trước NVL cần dùng theo BOM × SL (quyền inventory), Inter1 #48. */
public record ProductionMaterialPreviewResponse(
        Long productId,
        String productCode,
        String productName,
        BigDecimal plannedQty,
        BigDecimal estimatedMaterialCost,
        boolean allMaterialsSufficient,
        List<ProductionMaterialNeedResponse> materials) {}
