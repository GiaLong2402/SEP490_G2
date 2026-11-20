package com.hbsisms.feature.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Một dòng nhận hàng khi PATCH status=received (task #30). Luôn cộng tồn khả dụng tại
 * {@code locationId}. {@code passed} giữ tương thích FE — bị bỏ qua (không còn khu hàng trả).
 */
public record ReturnInspectLine(
        @NotNull Long variantId,
        Long lotId,
        @NotNull Long locationId,
        @NotNull Boolean passed,
        @Min(1) int qty) {}
