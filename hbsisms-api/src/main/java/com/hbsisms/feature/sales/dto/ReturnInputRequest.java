package com.hbsisms.feature.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of POST /returns — khớp FE {@code ReturnInput}; {@code deliveryId} thêm cho nhận hàng về khi
 * giao thất bại (Inter 3).
 */
public record ReturnInputRequest(
        @NotNull(message = "Vui lòng chọn đơn hàng") Long orderId,
        Long deliveryId,
        @NotBlank(message = "Vui lòng nhập lý do") @Size(max = 255) String reason,
        @NotNull @Valid @Size(min = 1, message = "Phiếu trả phải có ít nhất một dòng")
                List<ReturnLineInput> items) {}
