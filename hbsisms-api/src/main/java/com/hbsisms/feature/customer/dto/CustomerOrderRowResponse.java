package com.hbsisms.feature.customer.dto;

import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import java.math.BigDecimal;
import java.time.Instant;

/** Matches FE {@code CustomerOrderRow} on customer detail “Đơn gần đây”. */
public record CustomerOrderRowResponse(
        Long id, String code, Instant placedAt, BigDecimal total, OrderState state) {

    public static CustomerOrderRowResponse from(SalesOrder order) {
        return new CustomerOrderRowResponse(
                order.getId(), order.getCode(), order.getPlacedAt(), order.getTotal(), order.getState());
    }
}
