package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.PriceBasis;
import com.hbsisms.feature.sales.SalesOrderLine;
import com.hbsisms.feature.sales.SellUnit;
import java.math.BigDecimal;

/** Matches FE {@code OrderLineView}. */
public record OrderLineViewResponse(
        Long id,
        Long orderId,
        Long variantId,
        SellUnit unit,
        int qty,
        int qtyKhay,
        BigDecimal unitPrice,
        BigDecimal discountPct,
        BigDecimal lineTotal,
        PriceBasis priceBasis,
        int outstandingQty,
        String note,
        String sku,
        String productName,
        String packLabel,
        int deliveredKhay) {

    public static OrderLineViewResponse from(SalesOrderLine line, int deliveredKhay) {
        var variant = line.getVariant();
        var product = variant.getProduct();
        return new OrderLineViewResponse(
                line.getId(),
                line.getOrder().getId(),
                variant.getId(),
                line.getUnit(),
                line.getQty(),
                line.getQtyKhay(),
                line.getUnitPrice(),
                line.getDiscountPct(),
                line.getLineTotal(),
                line.getPriceBasis(),
                line.getOutstandingQty(),
                line.getNote(),
                variant.getSku(),
                product.getName(),
                variant.getPackLabel(),
                deliveredKhay);
    }
}
