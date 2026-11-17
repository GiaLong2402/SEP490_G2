package com.hbsisms.feature.sales.dto;

/** Đếm đơn theo tab quản lý (Inter 3 dòng 5 — GET /orders/counts). */
public record OrderCountsResponse(
        long awaitingPayment,
        long awaitingPrepare,
        long preparing,
        long ready,
        long delivering,
        long deliveryFailed,
        long needsRefund,
        long completed,
        long cancelled) {
}
