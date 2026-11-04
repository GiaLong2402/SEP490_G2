package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.inventory.StocktakeLine;

/** Matches FE {@code StocktakeLineView}. */
public record StocktakeLineResponse(
        Long id,
        Long stocktakeId,
        Long variantId,
        Long lotId,
        Long locationId,
        int systemQty,
        Integer countedQty,
        int diff,
        String sku,
        String productName,
        String lotCode,
        String locationCode) {

    public static StocktakeLineResponse from(StocktakeLine line, boolean multiWarehouse) {
        Integer counted = line.getCountedQty();
        int diff = counted == null ? 0 : counted - line.getSystemQty();
        String locationCode = null;
        if (line.getLocation() != null) {
            locationCode = multiWarehouse
                    ? line.getLocation().getWarehouse().getCode() + "/" + line.getLocation().getCode()
                    : line.getLocation().getCode();
        }
        return new StocktakeLineResponse(
                line.getId(),
                line.getStocktake().getId(),
                line.getVariant().getId(),
                line.getLot() == null ? null : line.getLot().getId(),
                line.getLocation() == null ? null : line.getLocation().getId(),
                line.getSystemQty(),
                counted,
                diff,
                line.getVariant().getSku(),
                line.getVariant().getProduct().getName(),
                line.getLot() == null ? null : line.getLot().getLotCode(),
                locationCode);
    }
}
