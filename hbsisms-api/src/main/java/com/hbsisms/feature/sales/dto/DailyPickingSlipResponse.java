package com.hbsisms.feature.sales.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Bản gộp soạn hàng theo ngày giao. Khớp FE {@code DailyPickingSlip}. */
public record DailyPickingSlipResponse(
        LocalDate date, Instant generatedAt, List<DailyPickingGroupResponse> groups) {}
