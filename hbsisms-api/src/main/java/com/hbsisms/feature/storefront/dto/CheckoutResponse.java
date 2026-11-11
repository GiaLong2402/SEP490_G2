package com.hbsisms.feature.storefront.dto;

import com.hbsisms.feature.sales.dto.OrderDetailResponse;

/** 201 body of POST /checkout. */
public record CheckoutResponse(OrderDetailResponse order, CheckoutPaymentResponse payment) {
}
