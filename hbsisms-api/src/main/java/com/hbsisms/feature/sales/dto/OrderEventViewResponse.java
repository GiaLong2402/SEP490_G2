package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.OrderEvent;
import java.time.Instant;

/** Matches FE {@code OrderEventView}. */
public record OrderEventViewResponse(
        Long id,
        Long orderId,
        String eventType,
        String description,
        Long createdBy,
        Instant createdAt,
        String actorName) {

    public static OrderEventViewResponse from(OrderEvent event) {
        var actor = event.getCreatedBy();
        return new OrderEventViewResponse(
                event.getId(),
                event.getOrder().getId(),
                event.getEventType(),
                event.getDescription(),
                actor == null ? null : actor.getId(),
                event.getCreatedAt(),
                actor == null ? null : actor.getFullName());
    }
}
