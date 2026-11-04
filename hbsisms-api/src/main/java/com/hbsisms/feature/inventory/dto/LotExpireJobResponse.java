package com.hbsisms.feature.inventory.dto;

/** Result of the daily lot-expire job (task 38). */
public record LotExpireJobResponse(int scanned, int expired, int failed, String asOfDate) {}
