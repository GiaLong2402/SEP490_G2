package com.hbsisms.feature.catalog;

/** Task 59 — nguồn giá vốn để gợi ý giá bán. */
public enum SellPriceCostSource {
    /**
     * WAC liên hoàn hiện hành trên số dư chạy (không phụ thuộc kỳ OPEN):
     * {@code (GT tồn trước + GT nhập) / (SL tồn trước + SL nhập)}.
     */
    WAC,
    /** Giá thành / khay của lệnh SX hoàn thành mới nhất (chỉ đối chiếu, không khuyến nghị làm giá bán). */
    PRODUCTION
}
