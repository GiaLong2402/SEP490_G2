package com.hbsisms.feature.production.dto;

import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import java.util.List;

/** Thành phẩm đã có BOM — list cho thủ kho (quyền inventory), Inter1 #48. */
public record ProductionBomProductResponse(
        Long id, String code, String name, boolean isProducible, long bomLineCount, List<VariantOption> variants) {

    /** Loại khay chọn khi lập lệnh (BOM tính cho 1 kg, khay 0,5 kg dùng một nửa nguyên liệu cân ký). */
    public record VariantOption(Long id, String sku, String packLabel, Integer unitWeightG) {
        public static VariantOption from(ProductVariant variant) {
            return new VariantOption(
                    variant.getId(), variant.getSku(), variant.getPackLabel(), variant.getUnitWeightG());
        }
    }

    public static ProductionBomProductResponse from(Product product, long bomLineCount) {
        return from(product, bomLineCount, List.of());
    }

    public static ProductionBomProductResponse from(
            Product product, long bomLineCount, List<ProductVariant> variants) {
        return new ProductionBomProductResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.isProducible(),
                bomLineCount,
                variants.stream().map(VariantOption::from).toList());
    }
}
