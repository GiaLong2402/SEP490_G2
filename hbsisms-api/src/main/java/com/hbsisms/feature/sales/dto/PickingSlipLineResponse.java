package com.hbsisms.feature.sales.dto;

import java.time.LocalDate;

/** Một dòng phiếu soạn theo lô / vị trí HOLD. Khớp FE {@code PickingSlipLine}. */
public record PickingSlipLineResponse(
        Long variantId,
        String productName,
        String sku,
        String packLabel,
        Long lotId,
        String lotCode,
        LocalDate expDate,
        Long warehouseId,
        String warehouseCode,
        Long locationId,
        String locationCode,
        String zone,
        int qtyKhay) {}
