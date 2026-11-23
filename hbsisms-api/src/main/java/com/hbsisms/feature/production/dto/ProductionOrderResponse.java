package com.hbsisms.feature.production.dto;

import com.hbsisms.feature.inventory.GoodsReceipt;
import com.hbsisms.feature.production.MaterialIssue;
import com.hbsisms.feature.production.ProductionOrder;
import com.hbsisms.feature.production.ProductionOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProductionOrderResponse(
        Long id,
        String code,
        ProductionOrderStatus status,
        Long productId,
        String productCode,
        String productName,
        BigDecimal plannedQty,
        LocalDate plannedDueDate,
        String note,
        BigDecimal estimatedProductionCost,
        BigDecimal estimatedMaterialCost,
        BigDecimal actualQty,
        BigDecimal actualProductionCost,
        BigDecimal actualMaterialCost,
        Long finishedVariantId,
        String finishedVariantSku,
        Long finishedReceiptId,
        String finishedReceiptCode,
        boolean allMaterialsSufficient,
        int materialLineCount,
        Long materialIssueId,
        String materialIssueCode,
        String creatorName,
        Instant createdAt,
        Instant approvedAt,
        String approverName,
        List<ProductionOrderMaterialLineResponse> materials) {

    public static ProductionOrderResponse from(
            ProductionOrder order, List<ProductionOrderMaterialLineResponse> materials) {
        MaterialIssue issue = order.getMaterialIssue();
        GoodsReceipt finished = order.getFinishedReceipt();
        var variant = order.getFinishedVariant();
        return new ProductionOrderResponse(
                order.getId(),
                order.getCode(),
                order.getStatus(),
                order.getProduct().getId(),
                order.getProduct().getCode(),
                order.getProduct().getName(),
                order.getPlannedQty(),
                order.getPlannedDueDate(),
                order.getNote(),
                order.getEstimatedProductionCost(),
                order.getEstimatedMaterialCost(),
                order.getActualQty(),
                order.getActualProductionCost(),
                order.getActualMaterialCost(),
                variant == null ? null : variant.getId(),
                variant == null ? null : variant.getSku(),
                finished == null ? null : finished.getId(),
                finished == null ? null : finished.getCode(),
                order.allMaterialsSufficient(),
                materials.size(),
                issue == null ? null : issue.getId(),
                issue == null ? null : issue.getCode(),
                order.getCreatedBy() == null ? null : order.getCreatedBy().getFullName(),
                order.getCreatedAt(),
                order.getApprovedAt(),
                order.getApprovedBy() == null ? null : order.getApprovedBy().getFullName(),
                materials);
    }
}
