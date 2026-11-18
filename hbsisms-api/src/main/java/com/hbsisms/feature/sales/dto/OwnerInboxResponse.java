package com.hbsisms.feature.sales.dto;

import java.util.List;

/** GET /orders/owner-inbox (Inter 3 dòng 11). */
public record OwnerInboxResponse(List<OwnerInboxItemResponse> items) {}
