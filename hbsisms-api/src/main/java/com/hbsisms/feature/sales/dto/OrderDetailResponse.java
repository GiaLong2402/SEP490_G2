package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.customer.dto.AddressResponse;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.AllocationStatus;
import com.hbsisms.feature.sales.FulfillmentMethod;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.OrderType;
import com.hbsisms.feature.sales.SaleChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Matches FE {@code OrderDetail}. */
public record OrderDetailResponse(
        Long id,
        String code,
        Long customerId,
        OrderType orderType,
        SaleChannel channel,
        Long sourceQuoteId,
        PaymentMethod paymentMethod,
        short paymentTermDays,
        PaymentStatus paymentStatus,
        AllocationStatus allocationStatus,
        FulfillmentMethod fulfillmentMethod,
        Long shipAddressId,
        OrderState state,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal shippingFee,
        BigDecimal total,
        String voucherCode,
        boolean isOverLimit,
        BigDecimal overLimitAmount,
        Long approvedBy,
        Long placedBy,
        Instant placedAt,
        Instant holdExpiresAt,
        String note,
        Instant createdAt,
        Instant updatedAt,
        String customerName,
        CustomerType customerType,
        OrderCustomerResponse customer,
        AddressResponse address,
        List<OrderLineViewResponse> lines,
        List<OrderDeliveryViewResponse> deliveries,
        List<PaymentViewResponse> payments,
        List<OrderEventViewResponse> events,
        BigDecimal paidAmount,
        String sourceQuoteCode,
        String placedByName,
        String approvedByName,
        List<OrderState> nextStates,
        boolean canCancel) {
}
