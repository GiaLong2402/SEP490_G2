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
        Long assignedTo,
        String carrier,
        List<DeliveryItem> items,
        DeliveryState state,
        Instant deliveredAt,
        String note,
        String assigneeName) {

    public static OrderDeliveryViewResponse from(Delivery delivery) {
        var assignee = delivery.getAssignedTo();
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
                assignee == null ? null : assignee.getId(),
                delivery.getCarrier(),
                delivery.getItems(),
                delivery.getState(),
                delivery.getDeliveredAt(),
                delivery.getNote(),
                assignee == null ? null : assignee.getFullName());
    }
}
