package com.hbsisms.feature.sales.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /sales-requests/{id}/reject. */
public record SalesRequestRejectRequest(
        @NotBlank(message = "Vui lòng nhập lý do từ chối") String rejectReason) {}
