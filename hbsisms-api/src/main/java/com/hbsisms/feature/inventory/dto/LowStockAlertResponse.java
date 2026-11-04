package com.hbsisms.feature.inventory.dto;

/** One low-stock variant for dashboard / alert panels. */
public record LowStockAlertResponse(String sku, String productName, int available) {}
