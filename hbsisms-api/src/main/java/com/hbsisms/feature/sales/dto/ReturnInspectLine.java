package com.hbsisms.feature.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Một dòng kiểm hàng khi nhận phiếu trả (task #30). {@code passed=true} → nhập lại
 * {@code locationId}; {@code false} → cách ly tại vị trí đó (hoặc vị trí cách ly mặc định).
 */
public record ReturnInspectLine(
        @NotNull Long variantId,
        Long lotId,
        @NotNull Long locationId,
        @NotNull Boolean passed,
        @Min(1) int qty) {}
