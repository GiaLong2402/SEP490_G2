package com.hbsisms.feature.customer.dto;

import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.catalog.CustomerPrice;
import com.hbsisms.feature.catalog.ProductVariant;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Matches FE {@code CustomerPriceRow} on customer detail. */
public record CustomerPriceRowResponse(
        Long id,
        Long customerId,
        Long variantId,
        BigDecimal unitPrice,
        ApprovalStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Long approvedBy,
        Instant approvedAt,
        Instant updatedAt,
        BigDecimal pendingPrice,
        String sku,
        String productName,
        String packLabel,
        BigDecimal listPrice,
        /** Ghi chú lần quyết định gần nhất (từ approval_task). */
        String decisionNote,
        /**
         * Trạng thái lần quyết định gần nhất ({@code approved}/{@code rejected}).
         * Null khi đang chờ duyệt. FE dùng để phân biệt note duyệt giá đang dùng vs từ chối đề xuất mới.
         */
        ApprovalStatus lastDecision) {

    public static CustomerPriceRowResponse from(
            CustomerPrice price, String decisionNote, ApprovalStatus lastDecision) {
        ProductVariant variant = price.getVariant();
        return new CustomerPriceRowResponse(
                price.getId(),
                price.getCustomer().getId(),
                variant.getId(),
                price.getUnitPrice(),
                price.getStatus(),
                price.getEffectiveFrom(),
                price.getEffectiveTo(),
                price.getApprovedBy() == null ? null : price.getApprovedBy().getId(),
                price.getApprovedAt(),
                price.getUpdatedAt(),
                price.getPendingPrice(),
                variant.getSku(),
                variant.getProduct().getName(),
                variant.getPackLabel(),
                variant.getListPrice(),
                decisionNote,
                lastDecision);
    }
}
