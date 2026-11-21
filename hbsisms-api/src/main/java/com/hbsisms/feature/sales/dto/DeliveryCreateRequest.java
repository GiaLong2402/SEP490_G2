package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.DeliveryItem;
import com.hbsisms.feature.sales.FulfillmentMethod;
import java.util.List;

/**
 * Body {@code POST /deliveries} — khớp {@code DeliveryInput} của web nội bộ.
 * {@code legLabel}, {@code inRadius}, {@code distanceKm} FE gửi kèm nhưng BE tự tính lại (không nhận ở đây).
 *
 * @param scheduledDate {@code yyyy-MM-dd}
 * @param addressId bỏ trống → lấy địa chỉ giao trên đơn (không dùng cho tự lấy)
 */
public record DeliveryCreateRequest(
        Long orderId,
        String scheduledDate,
        String timeWindow,
        FulfillmentMethod fulfillmentMethod,
        Long addressId,
        List<DeliveryItem> items,
        String note) {}
