package com.hbsisms.feature.inventory.dto;

/** One near-expiry lot for dashboard / alert panels. */
public record NearExpiryAlertResponse(String lotCode, String productName, int daysLeft, int qty) {}
