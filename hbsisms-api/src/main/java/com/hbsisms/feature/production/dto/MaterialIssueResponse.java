package com.hbsisms.feature.production.dto;

import com.hbsisms.feature.production.MaterialIssue;
import com.hbsisms.feature.production.MaterialIssueStatus;
import java.time.Instant;
import java.util.List;

public record MaterialIssueResponse(
        Long id,
        String code,
        MaterialIssueStatus status,
        Long productionOrderId,
        String productionOrderCode,
        Long productId,
        String productCode,
        String productName,
        String creatorName,
        Instant createdAt,
        Instant completedAt,
        String completerName,
        String note,
        List<MaterialIssueLineResponse> lines) {

    public static MaterialIssueResponse from(MaterialIssue issue, List<MaterialIssueLineResponse> lines) {
        var order = issue.getProductionOrder();
        return new MaterialIssueResponse(
                issue.getId(),
                issue.getCode(),
                issue.getStatus(),
                order.getId(),
                order.getCode(),
                order.getProduct().getId(),
                order.getProduct().getCode(),
                order.getProduct().getName(),
                issue.getCreatedBy() == null ? null : issue.getCreatedBy().getFullName(),
                issue.getCreatedAt(),
                issue.getCompletedAt(),
                issue.getCompletedBy() == null ? null : issue.getCompletedBy().getFullName(),
                issue.getNote(),
                lines);
    }
}
