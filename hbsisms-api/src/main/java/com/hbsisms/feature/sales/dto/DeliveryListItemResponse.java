package com.hbsisms.feature.sales.dto;

import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.customer.CustomerAddress;
import com.hbsisms.feature.sales.Delivery;
import com.hbsisms.feature.sales.DeliveryItem;
import com.hbsisms.feature.sales.DeliveryState;
import com.hbsisms.feature.sales.FulfillmentMethod;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Một dòng màn Giao hàng — khớp FE {@code DeliveryListItem} (web nội bộ). */
public record DeliveryListItemResponse(
        Long id,
        String code,
        Long orderId,
        String legLabel,
        LocalDate scheduledDate,
        String timeWindow,
        FulfillmentMethod fulfillmentMethod,
        Long addressId,
        boolean inRadius,
        BigDecimal distanceKm,
        List<DeliveryItem> items,
        DeliveryState state,
        Instant startedAt,
        Instant deliveredAt,
        String note,
        String orderCode,
        OrderState orderState,
        String customerName,
        String addressText,
        int totalKhay) {

    /**
     * @param orderTotalKhay tổng khay của cả đơn — dùng khi đợt giao "Trọn đơn" không có danh sách hàng
     *     ({@code items} rỗng); {@code null} nếu không có.
     */
    public static DeliveryListItemResponse from(Delivery delivery, Integer orderTotalKhay) {
        SalesOrder order = delivery.getOrder();
        CustomerAddress address = delivery.getAddress();
        List<DeliveryItem> items = delivery.getItems();
        return new DeliveryListItemResponse(
                delivery.getId(),
                delivery.getCode(),
                order.getId(),
                delivery.getLegLabel(),
                delivery.getScheduledDate(),
                delivery.getTimeWindow(),
                delivery.getFulfillmentMethod(),
                address == null ? null : address.getId(),
                delivery.isInRadius(),
                delivery.getDistanceKm(),
                items,
                delivery.getState(),
                delivery.getStartedAt(),
                delivery.getDeliveredAt(),
                delivery.getNote(),
                order.getCode(),
                order.getState(),
                customerNameOf(order),
                addressTextOf(address),
                items.isEmpty() && orderTotalKhay != null ? orderTotalKhay : totalKhayOf(items));
    }

    /** Giống danh sách đơn: tên snapshot trên đơn → tên khách hiện tại → "Khách vãng lai". */
    static String customerNameOf(SalesOrder order) {
        String snapshot = Strings.trimToNull(order.getCustomerName());
        if (snapshot != null) {
            return snapshot;
        }
        return order.getCustomer() == null ? "Khách vãng lai" : order.getCustomer().getName();
    }

    /** "Số nhà/đường, Phường, Tỉnh" (thiếu tỉnh thì lấy quận/huyện); không có địa chỉ (tự lấy) → null. */
    static String addressTextOf(CustomerAddress address) {
        if (address == null) {
            return null;
        }
        String area = Strings.trimToNull(address.getProvince()) != null
                ? address.getProvince()
                : address.getDistrict();
        String text = Stream.of(address.getLine1(), address.getWard(), area)
                .map(Strings::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return text.isEmpty() ? null : text;
    }

    static int totalKhayOf(List<DeliveryItem> items) {
        if (items == null) {
            return 0;
        }
        return items.stream().filter(Objects::nonNull).mapToInt(DeliveryItem::qtyKhay).sum();
    }
}
