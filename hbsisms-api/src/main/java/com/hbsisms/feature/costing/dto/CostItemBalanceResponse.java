package com.hbsisms.feature.costing.dto;

import com.hbsisms.feature.costing.CostItemBalance;
import com.hbsisms.feature.costing.CostItemType;
import java.math.BigDecimal;

public record CostItemBalanceResponse(
        Long id,
        Long periodId,
        CostItemType itemType,
        Long itemId,
        String itemCode,
        String itemName,
        BigDecimal openingQty,
        BigDecimal openingValue,
        BigDecimal inboundQty,
        BigDecimal inboundValue,
        BigDecimal outboundQty,
        BigDecimal outboundValue,
        BigDecimal closingQty,
        BigDecimal closingValue,
        BigDecimal avgUnitCost) {

    public static CostItemBalanceResponse of(CostItemBalance row, String itemCode, String itemName) {
        return new CostItemBalanceResponse(
                row.getId(),
                row.getPeriod().getId(),
                row.getItemType(),
                row.getItemId(),
                itemCode,
                itemName,
                row.getOpeningQty(),
                row.getOpeningValue(),
                row.getInboundQty(),
                row.getInboundValue(),
                row.getOutboundQty(),
                row.getOutboundValue(),
                row.getClosingQty(),
                row.getClosingValue(),
                row.getAvgUnitCost());
    }
}
