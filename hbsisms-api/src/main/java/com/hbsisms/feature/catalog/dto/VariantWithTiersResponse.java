package com.hbsisms.feature.catalog.dto;

import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.VolumeTier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Matches FE {@code VariantWithTiers}. */
public record VariantWithTiersResponse(
        Long id,
        Long productId,
        String sku,
        String packLabel,
        Integer unitWeightG,
        BigDecimal listPrice,
        int packPerCase,
        String barcode,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt,
        List<VolumeTierResponse> tiers,
        long available) {

    public static VariantWithTiersResponse from(
            ProductVariant variant, List<VolumeTier> tiers, long available) {
        return new VariantWithTiersResponse(
                variant.getId(),
                variant.getProduct().getId(),
                variant.getSku(),
                variant.getPackLabel(),
                variant.getUnitWeightG(),
                variant.getListPrice(),
                variant.getPackPerCase(),
                variant.getBarcode(),
                variant.isActive(),
                variant.getCreatedAt(),
                variant.getUpdatedAt(),
                tiers.stream().map(VolumeTierResponse::from).toList(),
                available);
    }
}
