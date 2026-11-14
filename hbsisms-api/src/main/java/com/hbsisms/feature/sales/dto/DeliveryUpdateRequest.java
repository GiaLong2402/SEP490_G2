package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.DeliveryState;

/** Matches FE {@code DeliveryUpdate}. */
public record DeliveryUpdateRequest(DeliveryState state, Long assignedTo, String note) {}
