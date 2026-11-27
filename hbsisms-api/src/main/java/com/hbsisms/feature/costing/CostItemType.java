package com.hbsisms.feature.costing;

/**
 * Loại mặt hàng trên số dư kỳ WAC.
 * {@link #RAW_MATERIAL} → {@code raw_material.id}; {@link #FINISHED} → {@code product_variant.id}.
 */
public enum CostItemType {
    RAW_MATERIAL,
    FINISHED
}
