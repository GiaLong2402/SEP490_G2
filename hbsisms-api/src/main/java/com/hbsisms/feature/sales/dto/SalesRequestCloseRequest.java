package com.hbsisms.feature.sales.dto;

/** Body of PATCH /sales-requests/{id} (đóng yêu cầu). */
public record SalesRequestCloseRequest(String status, String note) {}
