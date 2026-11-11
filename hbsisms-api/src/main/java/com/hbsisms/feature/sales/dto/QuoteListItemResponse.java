package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.Quote;
import com.hbsisms.feature.sales.QuoteStatus;
import com.hbsisms.feature.sales.SalesOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Matches FE {@code QuoteListItem}. */
public record QuoteListItemResponse(
        Long id,
        String code,
        Long customerId,
        Long requestId,
        QuoteStatus status,
        BigDecimal subtotal,
        BigDecimal total,
        LocalDate validUntil,
        Long createdBy,
        Long approvedBy,
        String note,
        Instant createdAt,
        Instant updatedAt,
        String customerName,
        String creatorName,
        Long convertedOrderId) {

    public static QuoteListItemResponse from(Quote quote, SalesOrder converted) {
        var customer = quote.getCustomer();
        var creator = quote.getCreatedBy();
        var request = quote.getRequest();
        return new QuoteListItemResponse(
                quote.getId(),
                quote.getCode(),
                customer == null ? null : customer.getId(),
                request == null ? null : request.getId(),
                quote.getStatus(),
                quote.getSubtotal(),
                quote.getTotal(),
                quote.getValidUntil(),
                creator == null ? null : creator.getId(),
                quote.getApprovedBy() == null ? null : quote.getApprovedBy().getId(),
                quote.getNote(),
                quote.getCreatedAt(),
                quote.getUpdatedAt(),
                customer == null ? null : customer.getName(),
                creator == null ? null : creator.getFullName(),
                converted == null ? null : converted.getId());
    }
}
