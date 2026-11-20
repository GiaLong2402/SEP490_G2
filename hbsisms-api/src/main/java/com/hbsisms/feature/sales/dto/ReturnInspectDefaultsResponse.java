package com.hbsisms.feature.sales.dto;

import java.util.List;

/**
 * Gợi ý vị trí khi NV kho kiểm phiếu trả (task #30): đạt → vị trí đã xuất; không đạt → cách ly cùng kho.
 */
public record ReturnInspectDefaultsResponse(
        /** Fallback toàn shop (kho đầu có CL) — FE ưu tiên {@link Line#quarantineLocationId()}. */
        Long quarantineLocationId,
        String quarantineLabel,
        List<Line> lines) {

    public record Line(
            Long variantId,
            Long lotId,
            Long shippedLocationId,
            String shippedLocationLabel,
            /** Khu cách ly cùng kho với vị trí đã xuất (nếu suy được). */
            Long quarantineLocationId,
            String quarantineLocationLabel) {}
}
