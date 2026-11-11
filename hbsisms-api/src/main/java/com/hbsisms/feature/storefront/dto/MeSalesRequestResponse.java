package com.hbsisms.feature.storefront.dto;

import com.hbsisms.feature.sales.SalesRequest;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Portal {@code /me/sales-requests} shape — {@code status} UPPERCASE for external FE
 * (staff {@code SalesRequestResponse} keeps lowercase {@code @JsonValue}).
 */
public record MeSalesRequestResponse(
        Long id,
        String code,
        String summary,
        String status,
        Instant receivedAt,
        LocalDate neededDate,
        Integer requestedQty,
        Integer availableQty,
        Boolean feasible,
        Integer fulfillableQty,
        Integer shortageQty,
        LocalDate expectedDate,
        String rejectReason,
        String note,
        String orderCode) {

    public static MeSalesRequestResponse from(SalesRequest request) {
        return new MeSalesRequestResponse(
                request.getId(),
                request.getCode(),
                request.getSummary() == null ? "" : request.getSummary(),
                request.getStatus() == null ? null : request.getStatus().name(),
                request.getReceivedAt(),
                request.getNeededDate(),
                request.getRequestedQty(),
                request.getAvailableQty(),
                request.getFeasible(),
                request.getFulfillableQty(),
                request.getShortageQty(),
                request.getExpectedDate(),
                request.getRejectReason() == null ? "" : request.getRejectReason(),
                request.getNote() == null ? "" : request.getNote(),
                request.getOrderCode());
    }
}
