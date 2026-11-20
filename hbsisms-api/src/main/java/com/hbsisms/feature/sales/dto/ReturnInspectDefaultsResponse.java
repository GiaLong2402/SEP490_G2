package com.hbsisms.feature.sales.dto;

import java.util.List;

/**
 * Gợi ý vị trí khi NV kho nhận phiếu trả (task #30): cộng tồn về vị trí đã xuất.
 * Trường quarantine* giữ tương thích FE cũ — luôn null (không dùng khu hàng trả).
 */
public record ReturnInspectDefaultsResponse(
        Long quarantineLocationId,
        String quarantineLabel,
        List<Line> lines) {

    public record Line(
            Long variantId,
            Long lotId,
            Long shippedLocationId,
            String shippedLocationLabel,
            Long quarantineLocationId,
            String quarantineLocationLabel) {}
}
