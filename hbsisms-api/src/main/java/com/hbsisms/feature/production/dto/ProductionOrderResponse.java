package com.hbsisms.feature.production.dto;

import com.hbsisms.feature.inventory.GoodsReceipt;
import com.hbsisms.feature.inventory.dto.FinishedPutAwayResponse;
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
        LocalDate finishedDate,
        BigDecimal defectQty,
        String defectReason,
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
        Instant completedAt,
        /** Giá vốn / khay sau nhập TP (null nếu chưa hoàn thành). */
        BigDecimal unitCost,
        /** % markup cấu hình (app_config sell_price_markup_percent). */
        Integer markupPercent,
        /** Giá bán = unitCost × (1 + markup/100); lúc nhập TP đã ghi vào listPrice quy cách. */
        BigDecimal suggestedSellPrice,
        /** Giá niêm yết quy cách thành phẩm (sau nhập TP = suggestedSellPrice). */
        BigDecimal currentListPrice,
        /** Lô / HSD / vị trí thành phẩm đã nhập kho (null nếu chưa nhập). */
        FinishedPutAwayResponse finishedPutAway,
        List<ProductionOrderMaterialLineResponse> materials,
        List<ProductionLeftoverResponse> leftovers) {

    public static ProductionOrderResponse from(
            ProductionOrder order,
            List<ProductionOrderMaterialLineResponse> materials,
            List<ProductionLeftoverResponse> leftovers) {
        return from(order, materials, leftovers, null, null, null, null, null);
    }

    public static ProductionOrderResponse from(
            ProductionOrder order,
            List<ProductionOrderMaterialLineResponse> materials,
            List<ProductionLeftoverResponse> leftovers,
            BigDecimal unitCost,
            Integer markupPercent,
            BigDecimal suggestedSellPrice,
            BigDecimal currentListPrice,
            FinishedPutAwayResponse finishedPutAway) {
        MaterialIssue issue = order.getMaterialIssue();
        GoodsReceipt finished = order.getFinishedReceipt();
        var variant = order.getFinishedVariant();
        Instant completedAt = order.getStatus() == ProductionOrderStatus.COMPLETED && finished != null
                ? finished.getCompletedAt()
                : null;
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
                order.getFinishedDate(),
                order.getDefectQty(),
                order.getDefectReason(),
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
                completedAt,
                unitCost,
                markupPercent,
                suggestedSellPrice,
                currentListPrice != null
                        ? currentListPrice
                        : (variant == null ? null : variant.getListPrice()),
                finishedPutAway,
                materials,
                leftovers == null ? List.of() : leftovers);
    }
}
