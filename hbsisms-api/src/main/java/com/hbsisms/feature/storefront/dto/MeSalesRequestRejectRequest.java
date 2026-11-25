package com.hbsisms.feature.storefront.dto;

import jakarta.validation.constraints.NotBlank;

/** Body POST /me/sales-requests/{code}/reject — khách từ chối báo giá. */
public record MeSalesRequestRejectRequest(@NotBlank(message = "Vui lòng nhập lý do từ chối") String rejectReason) {}
