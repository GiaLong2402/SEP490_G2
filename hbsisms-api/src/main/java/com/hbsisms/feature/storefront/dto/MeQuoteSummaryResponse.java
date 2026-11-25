package com.hbsisms.feature.storefront.dto;

import com.hbsisms.feature.sales.Quote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Báo giá gắn yêu cầu — portal khách xem giá / Đồng ý / Từ chối. */
public record MeQuoteSummaryResponse(
        Long id,
        String code,
        String status,
        BigDecimal subtotal,
        BigDecimal total,
        LocalDate validUntil,
        List<MeQuoteLineResponse> lines) {

    public static MeQuoteSummaryResponse from(Quote quote) {
        return new MeQuoteSummaryResponse(
                quote.getId(),
                quote.getCode(),
                quote.getStatus() == null ? null : quote.getStatus().name(),
                quote.getSubtotal(),
                quote.getTotal(),
                quote.getValidUntil(),
                quote.getLines().stream().map(MeQuoteLineResponse::from).toList());
    }
}
