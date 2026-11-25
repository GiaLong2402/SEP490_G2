package com.hbsisms.feature.sales.dto;

import java.time.Instant;
import java.util.List;

/** Phiếu soạn theo đơn. Khớp FE {@code OrderPickingSlip}. */
public record PickingSlipResponse(
        Long orderId,
        String orderCode,
        String customerName,
        Instant generatedAt,
        List<PickingSlipLineResponse> lines) {}
