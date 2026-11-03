package com.hbsisms.feature.customer.pricing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

/** Body of POST /pricing/resolve. Matches FE {@code PricingRequest}. */
public record PricingRequest(
        Long customerId,

        String paymentMethod,

        @DecimalMin(value = "0", message = "Phí ship không được âm")
        BigDecimal shippingFee,

        @DecimalMin(value = "0", message = "Giảm giá không được âm")
        BigDecimal discountTotal,

        @NotEmpty(message = "Phải có ít nhất một dòng")
        @Valid
        List<PricingLineInput> lines) {
}
