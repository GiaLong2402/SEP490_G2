package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.ReturnStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body of PATCH /returns/{id}/status.
 *
 * <p>Khi {@code status=received}: có thể gửi {@code inspections} (đạt → nhập lại, không đạt → cách
 * ly). Bỏ trống thì mặc định đưa cả phiếu vào cách ly (khớp nút FE “Đã nhận hàng”).
 */
public record ReturnStatusRequest(
        @NotNull(message = "status là bắt buộc") ReturnStatus status,
        @Valid List<ReturnInspectLine> inspections) {

    public ReturnStatusRequest(ReturnStatus status) {
        this(status, null);
    }
}
