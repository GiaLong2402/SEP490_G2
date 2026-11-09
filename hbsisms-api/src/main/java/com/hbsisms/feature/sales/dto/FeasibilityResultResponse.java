package com.hbsisms.feature.sales.dto;

/** Kết quả ghi khả thi (+ đơn tạo kèm nếu khách đồng ý). */
public record FeasibilityResultResponse(SalesRequestResponse request, OrderDetailResponse order) {}
