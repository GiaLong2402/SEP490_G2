package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.RequestStatus;
import com.hbsisms.feature.sales.SaleChannel;
import com.hbsisms.feature.sales.SalesRequest;
import java.time.Instant;
import java.time.LocalDate;

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
        Long handledBy,
        String handlerName,
        String note,
        Boolean feasible,
        Integer fulfillableQty,
        Integer shortageQty,
        LocalDate expectedDate,
        String rejectReason) {

    public static SalesRequestResponse from(SalesRequest request) {
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
                handler == null ? null : handler.getId(),
                handler == null ? null : handler.getFullName(),
                request.getNote(),
                request.getFeasible(),
                request.getFulfillableQty(),
                request.getShortageQty(),
                request.getExpectedDate(),
                request.getRejectReason());
    }
}
