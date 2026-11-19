package com.hbsisms.feature.sales.dto;

import java.util.List;

/** GET /returns/returnable-orders item. */
public record ReturnableOrderResponse(
        Long id, String code, String customerName, List<ReturnableLineResponse> lines) {}
