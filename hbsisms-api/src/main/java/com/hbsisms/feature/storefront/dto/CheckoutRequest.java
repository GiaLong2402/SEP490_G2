package com.hbsisms.feature.storefront.dto;

import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.sales.FulfillmentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Body of POST /checkout (Inter 2 #27). */
public record CheckoutRequest(
        @NotNull(message = "fulfillmentMethod là bắt buộc") FulfillmentMethod fulfillmentMethod,
        Long shipAddressId,
        LocalDate requestedDate,
        @Size(max = 40) String timeWindow,
        @Size(max = 500) String note,
        PaymentMethod paymentMethod,
        @NotEmpty(message = "Đơn phải có ít nhất một dòng hàng") @Valid List<CheckoutLineRequest> lines) {
}
