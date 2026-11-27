package com.hbsisms.feature.catalog;

/** Task 59 — nguồn giá vốn để gợi ý giá bán. */
public enum SellPriceCostSource {
    /** Đơn giá bình quân kỳ WAC hiện hành (OPEN, hoặc CLOSED mới nhất). */
    WAC,
    /** Giá thành / khay của lệnh SX hoàn thành mới nhất. */
    PRODUCTION
}
