package com.hbsisms.feature.inventory.dto;

import java.time.LocalDate;

/** Lô thành phẩm đã nhập kho từ một phiếu: mã lô, HSD và vị trí cất (cho màn tổng kết lệnh sản xuất). */
public record FinishedPutAwayResponse(
        String lotCode,
        LocalDate expDate,
        String locationCode,
        String locationZone,
        String warehouseName) {}
