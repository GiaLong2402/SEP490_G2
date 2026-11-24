package com.hbsisms.feature.production.dto;

import com.hbsisms.feature.catalog.Product;

/** Thành phẩm đã có BOM — list cho thủ kho (quyền inventory), Inter1 #48. */
public record ProductionBomProductResponse(
        Long id, String code, String name, boolean isProducible, long bomLineCount) {

    public static ProductionBomProductResponse from(Product product, long bomLineCount) {
        return new ProductionBomProductResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.isProducible(),
                bomLineCount);
    }
}
