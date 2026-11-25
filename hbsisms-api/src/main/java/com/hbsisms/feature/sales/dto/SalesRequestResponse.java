package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.RequestStatus;
import com.hbsisms.feature.sales.SaleChannel;
import com.hbsisms.feature.sales.SalesRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Matches FE {@code RequestListItem} / detail. */
public record SalesRequestResponse(
        Long id,
        String code,
        Long customerId,
        String customerName,
        String contactPhone,
        String contactAddress,
        SaleChannel channel,
        String summary,
        RequestStatus status,
        Instant receivedAt,
        LocalDate neededDate,
        Long handledBy,
        String handlerName,
        String note,
        Boolean feasible,
        Integer fulfillableQty,
        Integer shortageQty,
        LocalDate expectedDate,
        String rejectReason,
        Integer requestedQty,
        Integer availableQty,
        String orderCode,
        List<SalesRequestLineResponse> lines) {

    public static SalesRequestResponse from(SalesRequest request) {
        return from(request, request.getLines().stream().map(SalesRequestLineResponse::from).toList());
    }

    public static SalesRequestResponse from(SalesRequest request, List<SalesRequestLineResponse> lines) {
        var customer = request.getCustomer();
        var handler = request.getHandledBy();
        String name = request.getCustomerName();
        if ((name == null || name.isBlank()) && customer != null) {
            name = customer.getName();
        }
        String phone = request.getContactPhone();
        if ((phone == null || phone.isBlank()) && customer != null) {
            phone = customer.getPhone();
        }
        return new SalesRequestResponse(
                request.getId(),
                request.getCode(),
                customer == null ? null : customer.getId(),
                name,
                phone,
                request.getContactAddress(),
                request.getChannel(),
                request.getSummary(),
                request.getStatus(),
                request.getReceivedAt(),
                request.getNeededDate(),
                handler == null ? null : handler.getId(),
                handler == null ? null : handler.getFullName(),
                request.getNote(),
                request.getFeasible(),
                request.getFulfillableQty(),
                request.getShortageQty(),
                request.getExpectedDate(),
                request.getRejectReason(),
                request.getRequestedQty(),
                request.getAvailableQty(),
                request.getOrderCode(),
                lines == null ? List.of() : lines);
    }
}