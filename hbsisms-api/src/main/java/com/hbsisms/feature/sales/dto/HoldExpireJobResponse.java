package com.hbsisms.feature.sales.dto;

/** Result of the payment-hold expiry job. */
public record HoldExpireJobResponse(int releasedOrders) {}
