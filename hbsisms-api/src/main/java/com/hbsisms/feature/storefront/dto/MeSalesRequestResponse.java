package com.hbsisms.feature.storefront.dto;

import com.hbsisms.feature.sales.SalesRequest;
import com.hbsisms.feature.sales.dto.SalesRequestLineResponse;
import com.hbsisms.feature.sales.dto.SalesRequestResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
        String orderCode,
        List<SalesRequestLineResponse> lines) {

    public static MeSalesRequestResponse from(SalesRequest request) {
        return from(request, request.getLines().stream().map(SalesRequestLineResponse::from).toList());
    }

    /** List portal — không fetch lines (tránh N+1). */
    public static MeSalesRequestResponse from(SalesRequest request, List<SalesRequestLineResponse> lines) {
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
                request.getOrderCode(),
                lines == null ? List.of() : lines);
    }

    public static MeSalesRequestResponse from(SalesRequestResponse staff) {
        return new MeSalesRequestResponse(
                staff.id(),
                staff.code(),
                staff.summary() == null ? "" : staff.summary(),
                staff.status() == null ? null : staff.status().name(),
                staff.receivedAt(),
                staff.neededDate(),
                staff.requestedQty(),
                staff.availableQty(),
                staff.feasible(),
                staff.fulfillableQty(),
                staff.shortageQty(),
                staff.expectedDate(),
                staff.rejectReason() == null ? "" : staff.rejectReason(),
                staff.note() == null ? "" : staff.note(),
                staff.orderCode(),
                staff.lines() == null ? List.of() : staff.lines());
    }
}
