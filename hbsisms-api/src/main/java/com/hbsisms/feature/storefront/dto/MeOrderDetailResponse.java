package com.hbsisms.feature.storefront.dto;

import com.hbsisms.feature.customer.dto.AddressResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Portal {@code /me/orders} detail — enums as UPPERCASE strings for external FE
 * (staff {@code OrderDetailResponse} keeps lowercase {@code @JsonValue}).
 */
public record MeOrderDetailResponse(
        Long id,
        String code,
        String state,
        String paymentStatus,
        String fulfillmentMethod,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal shippingFee,
        BigDecimal total,
        BigDecimal paidAmount,
        Instant placedAt,
        Instant holdExpiresAt,
        String note,
        boolean canCancel,
        String customerName,
        MeCustomer customer,
        AddressResponse address,
        List<MeLine> lines,
        List<MeDelivery> deliveries,
        List<MePayment> payments,
        List<MeEvent> events) {

    public record MeCustomer(String name, String phone) {}

    public record MeLine(
            Long id,
            Long variantId,
            String productName,
            String packLabel,
            String sku,
            int qtyKhay,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String imageUrl) {}

    public record MeDelivery(LocalDate scheduledDate, String timeWindow) {}

    public record MePayment(
            String method,
            String direction,
            String status,
            Instant paidAt,
            String checkoutUrl,
            Long payosOrderCode) {}

    public record MeEvent(Long id, String eventType, String description, Instant createdAt) {}
}
