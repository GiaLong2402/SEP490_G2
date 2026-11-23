package com.hbsisms.feature.production.dto;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Body xác nhận xuất NVL. {@code allocations} trống/null → server tự FEFO đủ qtyRequired từng NVL.
 */
public record MaterialIssueConfirmRequest(@Valid List<MaterialIssueAllocationRequest> allocations) {}
