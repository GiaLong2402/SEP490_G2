package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.ReturnStatus;
import com.hbsisms.feature.sales.SalesReturn;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Row of GET /returns — khớp FE {@code ReturnListItem}. */
public record ReturnListItemResponse(
        Long id,
        String code,
        Long orderId,
        String orderCode,
        Long deliveryId,
        Long customerId,
        String customerName,
        String reason,
        BigDecimal amount,
        List<ReturnItemViewResponse> items,
        ReturnStatus status,
        Long createdBy,
        String creatorName,
        Instant createdAt) {

    public static ReturnListItemResponse from(SalesReturn row, List<ReturnItemViewResponse> items) {
        return new ReturnListItemResponse(
                row.getId(),
                row.getCode(),
                row.getOrder() == null ? null : row.getOrder().getId(),
                row.getOrder() == null ? null : row.getOrder().getCode(),
                row.getDelivery() == null ? null : row.getDelivery().getId(),
                row.getCustomer() == null ? null : row.getCustomer().getId(),
                row.getCustomer() == null ? null : row.getCustomer().getName(),
                row.getReason(),
                row.getAmount(),
                items,
                row.getStatus(),
                row.getCreatedBy() == null ? null : row.getCreatedBy().getId(),
                row.getCreatedBy() == null ? null : row.getCreatedBy().getFullName(),
                row.getCreatedAt());
    }
}
