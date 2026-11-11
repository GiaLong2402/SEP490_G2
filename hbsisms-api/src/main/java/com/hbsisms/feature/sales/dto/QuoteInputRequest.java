package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.customer.pricing.dto.PricingLineInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** Body of POST /quotes. Matches FE {@code QuoteInput}. */
public record QuoteInputRequest(
        @NotNull(message = "Vui lòng chọn khách hàng")
        Long customerId,

        Long requestId,

        LocalDate validUntil,

        String note,

        @NotEmpty(message = "Báo giá phải có ít nhất một dòng hàng")
        @Valid
        List<PricingLineInput> lines) {}
