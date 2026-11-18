package com.hbsisms.feature.sales.dto;

import java.time.Instant;

/** Một việc cần Chủ xử lý (Inter 3 dòng 11). */
public record OwnerInboxItemResponse(
        String type,
        String title,
        Long orderId,
        String orderCode,
        Long refId,
        Instant createdAt,
        String link) {}
