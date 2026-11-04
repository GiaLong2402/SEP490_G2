package com.hbsisms.feature.catalog.dto;

import com.hbsisms.feature.catalog.VolumeTier;
import java.math.BigDecimal;

/** Matches FE {@code VolumeTier}. */
public record VolumeTierResponse(Long id, Long variantId, int minQtyKhay, BigDecimal unitPrice) {

    public static VolumeTierResponse from(VolumeTier tier) {
        return new VolumeTierResponse(
                tier.getId(),
                tier.getVariant().getId(),
                tier.getMinQtyKhay(),
                tier.getUnitPrice());
    }
}
