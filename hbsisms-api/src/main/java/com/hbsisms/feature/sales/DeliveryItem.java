package com.hbsisms.feature.sales;

/** One entry of {@code delivery.items} (jsonb): how many trays of an order line ship in this leg. */
public record DeliveryItem(Long orderLineId, int qtyKhay) {
}
