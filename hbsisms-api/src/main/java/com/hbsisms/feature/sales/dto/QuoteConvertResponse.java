package com.hbsisms.feature.sales.dto;

/** Body of POST /quotes/{id}/convert. Matches FE convert result. */
public record QuoteConvertResponse(Long orderId, String orderCode) {}
