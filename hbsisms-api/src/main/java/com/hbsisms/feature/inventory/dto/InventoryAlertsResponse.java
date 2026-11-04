package com.hbsisms.feature.inventory.dto;

import java.util.List;

/** Combined stock alerts for dashboard and inventory attention lists. */
public record InventoryAlertsResponse(
        int available,
        int lowStockThreshold,
        int expiryWarningDays,
        List<LowStockAlertResponse> lowStock,
        List<NearExpiryAlertResponse> nearExpiry) {}
