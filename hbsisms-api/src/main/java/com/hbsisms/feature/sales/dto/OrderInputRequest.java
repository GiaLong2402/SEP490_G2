package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.customer.pricing.dto.PricingLineInput;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.sales.FulfillmentMethod;
import com.hbsisms.feature.sales.OrderType;
import com.hbsisms.feature.sales.SaleChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** Body of POST /orders. Matches FE {@code OrderInput}. */
public record OrderInputRequest(
        /**
         * Null = nhập nhanh khách vãng lai; BE find-or-create hồ sơ B2C từ {@link #phone()} +
         * {@link #customerName()}.
         */
        Long customerId,

        /** Tên khi nhập nhanh vãng lai (bỏ qua nếu đã có {@code customerId}). */
        String customerName,

        /** SĐT bắt buộc khi {@code customerId} null — khóa find-or-create / link OTP sau này. */
        String phone,

        @NotNull(message = "orderType là bắt buộc")
        OrderType orderType,

        @NotNull(message = "channel là bắt buộc")
        SaleChannel channel,

        @NotNull(message = "paymentMethod là bắt buộc")
        PaymentMethod paymentMethod,

        @NotNull(message = "fulfillmentMethod là bắt buộc")
        FulfillmentMethod fulfillmentMethod,

        Long shipAddressId,

        /** Địa chỉ nhập nhanh khi vãng lai giao hàng (thay {@link #shipAddressId()}). */
        @Valid
        ShipAddressInput shipAddress,

        @DecimalMin(value = "0", message = "Phí ship không được âm")
        BigDecimal shippingFee,

        @DecimalMin(value = "0", message = "Giảm giá không được âm")
        BigDecimal discountTotal,

        String voucherCode,

        String note,

        @NotEmpty(message = "Đơn phải có ít nhất một dòng hàng")
        @Valid
        List<PricingLineInput> lines) {
}
