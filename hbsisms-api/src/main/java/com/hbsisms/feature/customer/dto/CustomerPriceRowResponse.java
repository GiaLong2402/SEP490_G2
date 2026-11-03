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
        /** Ghi chú duyệt/từ chối gần nhất (từ approval_task) — staff xem trên tab Giá riêng. */
        String decisionNote) {

    public static CustomerPriceRowResponse from(CustomerPrice price, String decisionNote) {
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
                decisionNote);
    }
}
