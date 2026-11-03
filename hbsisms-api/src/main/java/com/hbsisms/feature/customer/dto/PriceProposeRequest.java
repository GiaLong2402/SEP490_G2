package com.hbsisms.feature.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Body of POST /customers/{id}/prices. Matches FE {@code PriceProposal}. */
public record PriceProposeRequest(
        @NotNull(message = "variantId là bắt buộc")
        Long variantId,

        @NotNull(message = "Giá đề xuất là bắt buộc")
        @DecimalMin(value = "0.01", message = "Giá phải lớn hơn 0")
        BigDecimal unitPrice,

        LocalDate effectiveFrom,

        LocalDate effectiveTo) {
}
