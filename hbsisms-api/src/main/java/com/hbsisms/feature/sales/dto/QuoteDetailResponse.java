package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.Quote;
import com.hbsisms.feature.sales.QuoteStatus;
import com.hbsisms.feature.sales.SalesOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Matches FE {@code QuoteDetail}. */
public record QuoteDetailResponse(
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
        Long convertedOrderId,
        List<QuoteLineViewResponse> lines,
        String requestCode,
        String convertedOrderCode) {

    public static QuoteDetailResponse from(Quote quote, SalesOrder converted) {
        QuoteListItemResponse header = QuoteListItemResponse.from(quote, converted);
        var request = quote.getRequest();
        return new QuoteDetailResponse(
                header.id(),
                header.code(),
                header.customerId(),
                header.requestId(),
                header.status(),
                header.subtotal(),
                header.total(),
                header.validUntil(),
                header.createdBy(),
                header.approvedBy(),
                header.note(),
                header.createdAt(),
                header.updatedAt(),
                header.customerName(),
                header.creatorName(),
                header.convertedOrderId(),
                quote.getLines().stream().map(QuoteLineViewResponse::from).toList(),
                request == null ? null : request.getCode(),
                converted == null ? null : converted.getCode());
    }
}
