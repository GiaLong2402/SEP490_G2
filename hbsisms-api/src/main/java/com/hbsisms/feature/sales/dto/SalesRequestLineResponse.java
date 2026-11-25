package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.sales.SalesRequestLine;
import com.hbsisms.feature.sales.SellUnit;

public record SalesRequestLineResponse(
        Long id,
        Long variantId,
        String sku,
        String productName,
        String packLabel,
        short packPerCase,
        SellUnit unit,
        int qty,
        int qtyKhay,
        Integer fulfillableQty,
        Integer shortageQty) {

    public static SalesRequestLineResponse from(SalesRequestLine line) {
        ProductVariant variant = line.getVariant();
        String productName = variant.getProduct() == null ? null : variant.getProduct().getName();
        return new SalesRequestLineResponse(
                line.getId(),
                variant.getId(),
                variant.getSku(),
                productName,
                variant.getPackLabel(),
                variant.getPackPerCase(),
                line.getUnit(),
                line.getQty(),
                line.getQtyKhay(),
                line.getFulfillableQty(),
                line.getShortageQty());
    }
}
