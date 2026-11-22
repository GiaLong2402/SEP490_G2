package com.hbsisms.feature.production.dto;

import com.hbsisms.feature.production.MaterialIssueLine;
import java.math.BigDecimal;

public record MaterialIssueLineResponse(
        Long id,
        Long rawMaterialId,
        String rawMaterialCode,
        String rawMaterialName,
        String unit,
        BigDecimal qtyRequired,
        BigDecimal qtyIssued,
        Long rawMaterialLotId,
        String lotCode) {

    public static MaterialIssueLineResponse from(MaterialIssueLine line) {
        var raw = line.getRawMaterial();
        var lot = line.getRawMaterialLot();
        return new MaterialIssueLineResponse(
                line.getId(),
                raw.getId(),
                raw.getCode(),
                raw.getName(),
                raw.getUnit(),
                line.getQtyRequired(),
                line.getQtyIssued(),
                lot == null ? null : lot.getId(),
                lot == null ? null : lot.getLotCode());
    }
}
