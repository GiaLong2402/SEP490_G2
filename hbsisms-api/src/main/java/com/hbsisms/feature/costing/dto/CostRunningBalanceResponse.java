package com.hbsisms.feature.costing.dto;

import com.hbsisms.feature.costing.CostItemType;
import com.hbsisms.feature.costing.CostRunningBalance;
import java.math.BigDecimal;
import java.time.Instant;

public record CostRunningBalanceResponse(
        Long id,
        CostItemType itemType,
        Long itemId,
        String itemCode,
        String itemName,
        /** Đơn vị hiển thị cạnh SL (NVL: kg/cái…; TP: khay). */
        String unit,
        BigDecimal qtyOnHand,
        BigDecimal inventoryValue,
        BigDecimal avgUnitCost,
        Instant updatedAt) {

    public static CostRunningBalanceResponse of(
            CostRunningBalance row, String itemCode, String itemName, String unit) {
        return new CostRunningBalanceResponse(
                row.getId(),
                row.getItemType(),
                row.getItemId(),
                itemCode,
                itemName,
                unit,
                row.getQtyOnHand(),
                row.getInventoryValue(),
                row.getAvgUnitCost(),
                row.getUpdatedAt());
    }
}
