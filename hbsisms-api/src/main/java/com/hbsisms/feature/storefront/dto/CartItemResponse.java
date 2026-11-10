package com.hbsisms.feature.storefront.dto;

import com.hbsisms.feature.sales.PriceBasis;
import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long variantId,
        Long productId,
        String productName,
        String productSlug,
        String imageUrl,
        String sku,
        String packLabel,
        Integer unitWeightG,
        int qty,
        BigDecimal unitPrice,
        PriceBasis priceBasis,
        BigDecimal lineTotal) {}
