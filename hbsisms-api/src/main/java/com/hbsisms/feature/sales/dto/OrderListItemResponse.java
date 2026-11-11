package com.hbsisms.feature.sales.dto;

import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.AllocationStatus;
import com.hbsisms.feature.sales.FulfillmentMethod;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.OrderType;
import com.hbsisms.feature.sales.SaleChannel;
import com.hbsisms.feature.sales.SalesOrder;
import java.math.BigDecimal;
import java.time.Instant;

/** Matches FE {@code OrderListItem}. */
public record OrderListItemResponse(
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
        CustomerType customerType) {

    public static OrderListItemResponse from(SalesOrder order) {
        var customer = order.getCustomer();
        return new OrderListItemResponse(
                order.getId(),
                order.getCode(),
                customer == null ? null : customer.getId(),
                order.getOrderType(),
                order.getChannel(),
                order.getSourceQuote() == null ? null : order.getSourceQuote().getId(),
                order.getPaymentMethod(),
                order.getPaymentTermDays(),
                order.getPaymentStatus(),
                order.getAllocationStatus(),
                order.getFulfillmentMethod(),
                order.getShipAddress() == null ? null : order.getShipAddress().getId(),
                order.getState(),
                order.getSubtotal(),
                order.getDiscountTotal(),
                order.getShippingFee(),
                order.getTotal(),
                order.getVoucherCode(),
                order.isOverLimit(),
                order.getOverLimitAmount(),
                order.getApprovedBy() == null ? null : order.getApprovedBy().getId(),
                order.getPlacedBy() == null ? null : order.getPlacedBy().getId(),
                order.getPlacedAt(),
                order.getHoldExpiresAt(),
                order.getNote(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                customerNameOf(order),
                customer == null ? null : customer.getCustomerType());
    }

    private static String customerNameOf(SalesOrder order) {
        if (Strings.trimToNull(order.getCustomerName()) != null) {
            return order.getCustomerName().trim();
        }
        return order.getCustomer() == null ? "Khách vãng lai" : order.getCustomer().getName();
    }
}
