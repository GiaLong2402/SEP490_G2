package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.ReturnItem;
import java.math.BigDecimal;

/** Enriched return line for list/detail responses. */
public record ReturnItemViewResponse(
        Long variantId, Long lotId, int qty, BigDecimal amount, String sku, String productName) {

    public static ReturnItemViewResponse of(ReturnItem item, String sku, String productName) {
        return new ReturnItemViewResponse(
                item.variantId(), item.lotId(), item.qty(), item.amount(), sku, productName);
    }
}
