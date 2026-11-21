package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.Delivery;
import com.hbsisms.feature.sales.DeliveryItem;
import com.hbsisms.feature.sales.DeliveryState;
import com.hbsisms.feature.sales.FulfillmentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Matches FE {@code OrderDeliveryView}. */
public record OrderDeliveryViewResponse(
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
        String note) {

    public static OrderDeliveryViewResponse from(Delivery delivery) {
        return new OrderDeliveryViewResponse(
                delivery.getId(),
                delivery.getCode(),
                delivery.getOrder().getId(),
                delivery.getLegLabel(),
                delivery.getScheduledDate(),
                delivery.getTimeWindow(),
                delivery.getFulfillmentMethod(),
                delivery.getAddress() == null ? null : delivery.getAddress().getId(),
                delivery.isInRadius(),
                delivery.getDistanceKm(),
                delivery.getItems(),
                delivery.getState(),
                delivery.getStartedAt(),
                delivery.getDeliveredAt(),
                delivery.getNote());
    }
}
